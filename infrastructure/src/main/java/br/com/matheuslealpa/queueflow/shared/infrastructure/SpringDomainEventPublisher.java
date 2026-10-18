package br.com.matheuslealpa.queueflow.shared.infrastructure;

import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entrega os eventos de domínio a ouvintes internos via {@link ApplicationEventPublisher}, dentro
 * da transação corrente. Ouvintes que precisem de efeitos após o commit devem usar
 * {@code @TransactionalEventListener}. Entrega confiável para sistemas externos exigirá uma outbox.
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        for (var event : events) {
            log.debug("Evento de domínio: {}", event);
            publisher.publishEvent(event);
        }
    }
}
