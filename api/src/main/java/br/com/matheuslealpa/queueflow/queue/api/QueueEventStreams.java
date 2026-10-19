package br.com.matheuslealpa.queueflow.queue.api;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.infrastructure.realtime.QueueEvent;
import br.com.matheuslealpa.queueflow.shared.infrastructure.realtime.QueueEventHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conexões SSE abertas nesta instância. Cada uma assina o {@link QueueEventHub} da sua fila e
 * recebe um comentário periódico, que mantém proxies abertos e revela clientes que já saíram.
 */
@Component
@EnableScheduling
class QueueEventStreams {

    /** Intervalo sugerido ao EventSource para reconectar. */
    private static final long RECONNECT_MILLIS = 3_000;

    private final QueueEventHub hub;
    private final Duration timeout;
    private final Set<SseEmitter> open = ConcurrentHashMap.newKeySet();

    QueueEventStreams(QueueEventHub hub, @Value("${queueflow.events.stream-timeout:PT30M}") Duration timeout) {
        this.hub = hub;
        this.timeout = timeout;
    }

    SseEmitter open(OrganizationId organizationId, QueueId queueId) {
        var emitter = new SseEmitter(timeout.toMillis());
        var subscription = hub.subscribe(organizationId, queueId, event -> send(emitter, event));
        Runnable release = () -> {
            subscription.close();
            open.remove(emitter);
        };
        emitter.onCompletion(release);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> release.run());
        open.add(emitter);
        try {
            // confirma a assinatura: o cliente recarrega o estado a partir daqui
            emitter.send(SseEmitter.event().name("ready").reconnectTime(RECONNECT_MILLIS).data(queueId.value()));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${queueflow.events.heartbeat:PT20S}")
    void heartbeat() {
        for (var emitter : open) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }

    /**
     * Encerra as conexões no início do desligamento: sem isso o shutdown gracioso esperaria cada
     * SSE até o timeout. Os clientes reconectam em outra instância.
     */
    @EventListener(ContextClosedEvent.class)
    void closeAll() {
        open.forEach(SseEmitter::complete);
        open.clear();
    }

    int openCount() {
        return open.size();
    }

    private static void send(SseEmitter emitter, QueueEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
