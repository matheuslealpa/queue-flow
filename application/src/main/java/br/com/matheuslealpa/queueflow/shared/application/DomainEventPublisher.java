package br.com.matheuslealpa.queueflow.shared.application;

import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;

import java.util.List;

/**
 * Publica eventos de domínio dentro da transação corrente. A implementação decide se os
 * entrega a ouvintes internos e/ou os grava em uma outbox para integração externa.
 */
@FunctionalInterface
public interface DomainEventPublisher {

    void publish(List<DomainEvent> events);
}
