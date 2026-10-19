package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Assinantes locais (conexões SSE desta instância) por fila. O broker entrega aqui os eventos
 * recebidos, venham eles desta ou de outra instância da API.
 */
public class QueueEventHub {

    private static final Logger log = LoggerFactory.getLogger(QueueEventHub.class);

    private record Key(OrganizationId organizationId, QueueId queueId) {
    }

    /** Cancela a assinatura; idempotente. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private final Map<Key, Set<Consumer<QueueEvent>>> subscribers = new ConcurrentHashMap<>();

    public Subscription subscribe(OrganizationId organizationId, QueueId queueId, Consumer<QueueEvent> subscriber) {
        var key = new Key(organizationId, queueId);
        subscribers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(subscriber);
        return () -> subscribers.computeIfPresent(key, (k, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set;
        });
    }

    void dispatch(QueueEvent event) {
        var key = new Key(new OrganizationId(event.organizationId()), new QueueId(event.queueId()));
        for (var subscriber : subscribers.getOrDefault(key, Set.of())) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException e) {
                // um cliente com problema não pode impedir a entrega aos demais
                log.debug("Falha ao entregar {} a um assinante", event.type(), e);
            }
        }
    }

    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }
}
