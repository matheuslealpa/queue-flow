package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Repassa os eventos de domínio ao broker só depois do commit: um cliente avisado precisa
 * enxergar a mudança ao consultar a API. Falhas de entrega não afetam o caso de uso, que já foi
 * confirmado; os clientes ainda têm o polling de contingência.
 */
public class QueueEventRelay {

    private static final Logger log = LoggerFactory.getLogger(QueueEventRelay.class);

    private final QueueEventMapper mapper;
    private final QueueEventBroker broker;

    public QueueEventRelay(QueueEventMapper mapper, QueueEventBroker broker) {
        this.mapper = mapper;
        this.broker = broker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(DomainEvent event) {
        try {
            mapper.map(event).ifPresent(broker::publish);
        } catch (RuntimeException e) {
            log.warn("Não foi possível publicar {} em tempo real", event.getClass().getSimpleName(), e);
        }
    }
}
