package br.com.matheuslealpa.queueflow.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo HTTP completo contra a aplicação real (PostgreSQL via Testcontainers).
 */
@SpringBootTest(properties = "queueflow.calls.expiration.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QueueFlowApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    String org;
    String admin;
    String location;
    String service;
    String desk;
    String queue;
    String attendant;

    @BeforeEach
    void setUpOrganization() throws Exception {
        var created = body(mvc.perform(json(post("/api/v1/organizations"),
                        "{\"name\":\"Farmácia Central\",\"adminName\":\"Ana\"}"))
                .andExpect(status().isCreated()));
        org = JsonPath.read(created, "$.organizationId");
        admin = JsonPath.read(created, "$.adminOperatorId");

        location = id(asAdmin(post("/api/v1/locations"), "{\"name\":\"Matriz\",\"timeZone\":\"America/Sao_Paulo\"}"));
        service = id(asAdmin(post("/api/v1/services"), "{\"code\":\"retirada\",\"name\":\"Retirada\"}"));
        desk = id(asAdmin(post("/api/v1/service-points"), "{\"locationId\":\"%s\",\"name\":\"Guichê 1\"}"
                .formatted(location)));
        queue = id(asAdmin(post("/api/v1/queues"), """
                {"locationId":"%s","name":"Retirada","ticketPrefix":"R",
                 "policy":{"ordering":"PRIORITY_FIFO","callTimeoutSeconds":120,"maxCallAttempts":2,
                           "returnPosition":"KEEP_ORIGINAL","allowReturnFromService":true},
                 "acceptedServices":["%s"],
                 "classifications":[{"code":"PREFERENCIAL","label":"Preferencial","level":10,"restricted":false},
                                    {"code":"URGENTE","label":"Urgente","level":50,"restricted":true}]}
                """.formatted(location, service)));
        attendant = id(asAdmin(post("/api/v1/operators"),
                "{\"name\":\"Bruno\",\"permissions\":[\"CALL_ENTRIES\"]}"));
        mvc.perform(json(post("/api/v1/operators/{id}/assignments", attendant), "{\"queueId\":\"%s\"}".formatted(queue))
                        .header("X-Organization-Id", org).header("X-Operator-Id", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(1)));
    }

    @Test
    void fullAttendanceFlow() throws Exception {
        var first = enqueueFromKiosk("{\"serviceId\":\"%s\"}".formatted(service), null)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/entries/")))
                .andExpect(jsonPath("$.ticket").value("R001"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.priority.level").value(0));
        var preferential = enqueueFromKiosk(
                "{\"serviceId\":\"%s\",\"priorityClassification\":\"preferencial\"}".formatted(service), null)
                .andExpect(jsonPath("$.ticket").value("R002"))
                .andExpect(jsonPath("$.priority.level").value(10));
        var preferentialId = id(preferential);

        mvc.perform(asOrg(get("/api/v1/queues/{id}/entries", queue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticket").value("R002"))
                .andExpect(jsonPath("$[1].ticket").value("R001"));

        mvc.perform(asAttendant(post("/api/v1/queues/{id}/calls/next", queue), "{\"servicePointId\":\"%s\"}".formatted(desk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(preferentialId))
                .andExpect(jsonPath("$.status").value("CALLED"))
                .andExpect(jsonPath("$.call.servicePointId").value(desk));

        mvc.perform(asAttendant(post("/api/v1/entries/{id}/start", preferentialId), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_SERVICE"));
        mvc.perform(asAttendant(post("/api/v1/entries/{id}/complete", preferentialId), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.serviceSessions").value(1));

        // estado terminal: não volta a estados anteriores
        mvc.perform(asAttendant(post("/api/v1/entries/{id}/start", preferentialId), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://queueflow.dev/problems/invalid-state-transition"));

        mvc.perform(asOrg(get("/api/v1/queues/{id}/metrics", queue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(1))
                .andExpect(jsonPath("$.completed").value(1));

        var firstId = id(first);
        mvc.perform(json(post("/api/v1/entries/{id}/cancel", firstId), "{\"reason\":\"desistiu\"}")
                        .header("X-Organization-Id", org).header("X-Client-Channel", "totem-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(asAttendant(post("/api/v1/queues/{id}/calls/next", queue), "{\"servicePointId\":\"%s\"}".formatted(desk)))
                .andExpect(status().isNoContent());
    }

    @Test
    void idempotencyKeyReturnsSameEntryAndRejectsDifferentPayload() throws Exception {
        var request = "{\"serviceId\":\"%s\",\"externalReference\":\"PED-77\"}".formatted(service);
        var firstId = id(enqueueFromKiosk(request, "abc-123").andExpect(status().isCreated()));

        enqueueFromKiosk(request, "abc-123")
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.ticket").value("R001"));
        enqueueFromKiosk("{\"serviceId\":\"%s\",\"externalReference\":\"PED-78\"}".formatted(service), "abc-123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://queueflow.dev/problems/idempotency-conflict"));
        mvc.perform(asOrg(get("/api/v1/queues/{id}/entries", queue))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void mapsErrorsToProblemDetails() throws Exception {
        // sem identificação
        mvc.perform(get("/api/v1/queues/{id}", queue)).andExpect(status().isUnauthorized());
        // validação de entrada
        asAdminRequest(post("/api/v1/locations"), "{\"name\":\"\",\"timeZone\":\"UTC\"}")
                .andExpect(status().isBadRequest());
        // regra de negócio: serviço não aceito pela fila
        enqueueFromKiosk("{}", null).andExpect(status().isUnprocessableContent());
        // autorização: totem não pode atribuir prioridade restrita
        enqueueFromKiosk("{\"serviceId\":\"%s\",\"priorityClassification\":\"URGENTE\"}".formatted(service), null)
                .andExpect(status().isForbidden());
        // operador sem atribuição/permissão administrativa
        mvc.perform(json(put("/api/v1/queues/{id}/status", queue), "{\"status\":\"PAUSED\"}")
                        .header("X-Organization-Id", org).header("X-Operator-Id", attendant))
                .andExpect(status().isForbidden());
        // isolamento entre organizações
        mvc.perform(get("/api/v1/queues/{id}", queue).header("X-Organization-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void pausedQueueRejectsNewEntries() throws Exception {
        mvc.perform(json(put("/api/v1/queues/{id}/status", queue), "{\"status\":\"PAUSED\"}")
                        .header("X-Organization-Id", org).header("X-Operator-Id", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        enqueueFromKiosk("{\"serviceId\":\"%s\"}".formatted(service), null)
                .andExpect(status().isUnprocessableContent());
    }

    // ---- auxiliares ---------------------------------------------------------------------

    private ResultActions enqueueFromKiosk(String body, String idempotencyKey) throws Exception {
        var request = json(post("/api/v1/queues/{id}/entries", queue), body)
                .header("X-Organization-Id", org)
                .header("X-Client-Channel", "totem-1");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    private MockHttpServletRequestBuilder asOrg(MockHttpServletRequestBuilder request) {
        return request.header("X-Organization-Id", org);
    }

    private MockHttpServletRequestBuilder asAttendant(MockHttpServletRequestBuilder request, String body) {
        return json(request, body).header("X-Organization-Id", org).header("X-Operator-Id", attendant);
    }

    private ResultActions asAdminRequest(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(json(request, body).header("X-Organization-Id", org).header("X-Operator-Id", admin));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String body) throws Exception {
        return asAdminRequest(request, body).andExpect(status().isCreated());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }
}
