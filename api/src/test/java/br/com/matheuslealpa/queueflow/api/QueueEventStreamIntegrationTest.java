package br.com.matheuslealpa.queueflow.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eventos da fila por SSE numa conexão HTTP real (o MockMvc não acompanha respostas em fluxo).
 * Usa o broker em memória; a ida e volta pelo Redis é coberta na infraestrutura.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"queueflow.calls.expiration.enabled=false", "queueflow.events.broker=local"})
@Import(TestcontainersConfiguration.class)
class QueueEventStreamIntegrationTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    String org;
    String admin;
    String service;
    String desk;
    String queue;
    String attendant;

    @BeforeEach
    void setUp() throws Exception {
        var created = send("POST", "/organizations", null, "{\"name\":\"Clínica\",\"adminName\":\"Ana\"}");
        org = JsonPath.read(created, "$.organizationId");
        admin = JsonPath.read(created, "$.adminOperatorId");
        var location = id(send("POST", "/locations", admin, "{\"name\":\"Centro\",\"timeZone\":\"America/Sao_Paulo\"}"));
        service = id(send("POST", "/services", admin, "{\"code\":\"GERAL\",\"name\":\"Geral\"}"));
        desk = id(send("POST", "/service-points", admin,
                "{\"locationId\":\"%s\",\"name\":\"Guichê 1\"}".formatted(location)));
        queue = id(send("POST", "/queues", admin, """
                {"locationId":"%s","name":"Geral","ticketPrefix":"G",
                 "policy":{"ordering":"FIFO","callTimeoutSeconds":60,"maxCallAttempts":2,
                           "returnPosition":"KEEP_ORIGINAL","allowReturnFromService":true},
                 "acceptedServices":["%s"]}
                """.formatted(location, service)));
        attendant = id(send("POST", "/operators", admin, "{\"name\":\"Bruno\",\"permissions\":[\"CALL_ENTRIES\"]}"));
        send("POST", "/operators/%s/assignments".formatted(attendant), admin, "{\"queueId\":\"%s\"}".formatted(queue));
    }

    @Test
    void streamsQueueChangesToConnectedClients() throws Exception {
        var lines = openStream(org, queue);
        awaitLine(lines, "event:ready");

        send("POST", "/queues/%s/entries".formatted(queue), admin, "{\"serviceId\":\"%s\"}".formatted(service));
        assertThat(awaitLine(lines, "event:entry.created")).isNotNull();
        assertThat(awaitLine(lines, "data:")).contains("\"ticket\":\"G001\"");

        send("POST", "/queues/%s/calls/next".formatted(queue), attendant, "{\"servicePointId\":\"%s\"}".formatted(desk));
        assertThat(awaitLine(lines, "event:entry.called")).isNotNull();
        assertThat(awaitLine(lines, "data:")).contains("\"servicePointName\":\"Guichê 1\"");

        send("PUT", "/queues/%s/status".formatted(queue), admin, "{\"status\":\"PAUSED\"}");
        assertThat(awaitLine(lines, "event:queue.status-changed")).isNotNull();
        assertThat(awaitLine(lines, "data:")).contains("\"status\":\"PAUSED\"");
    }

    @Test
    void rejectsQueuesOfAnotherOrganization() throws Exception {
        var request = HttpRequest.newBuilder(api("/queues/%s/events".formatted(queue)))
                .header("Accept", "text/event-stream")
                .header("X-Organization-Id", UUID.randomUUID().toString())
                .GET().build();

        assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
    }

    @Test
    void requiresTheOrganizationHeader() throws Exception {
        var request = HttpRequest.newBuilder(api("/queues/%s/events".formatted(queue)))
                .header("Accept", "text/event-stream").GET().build();

        assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
    }

    private BlockingQueue<String> openStream(String organization, String queueId) {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        var request = HttpRequest.newBuilder(api("/queues/%s/events".formatted(queueId)))
                .header("Accept", "text/event-stream")
                .header("X-Organization-Id", organization)
                .GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> response.body().forEach(lines::add));
        return lines;
    }

    /** Próxima linha que começa com o prefixo, descartando as anteriores; falha após 5s. */
    private static String awaitLine(BlockingQueue<String> lines, String prefix) throws InterruptedException {
        List<String> skipped = new ArrayList<>();
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (line == null) {
                continue;
            }
            if (line.startsWith(prefix)) {
                return line;
            }
            skipped.add(line);
        }
        throw new AssertionError("Sem linha '%s' no fluxo; recebido: %s".formatted(prefix, skipped));
    }

    private String send(String method, String path, String operator, String body)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(api(path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (operator != null) {
            builder.header("X-Organization-Id", org).header("X-Operator-Id", operator);
        }
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isLessThan(300);
        return response.body();
    }

    private URI api(String path) {
        return URI.create("http://localhost:%d/api/v1%s".formatted(port, path));
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }
}
