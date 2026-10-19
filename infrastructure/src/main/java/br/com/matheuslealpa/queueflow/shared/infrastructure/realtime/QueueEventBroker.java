package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

/**
 * Distribui eventos de fila a todas as instâncias da API. Em produção é o Redis Pub/Sub; sem
 * Redis (testes, instância única) a entrega fica em memória.
 */
@FunctionalInterface
public interface QueueEventBroker {

    void publish(QueueEvent event);

    /** Entrega direto aos assinantes desta instância. */
    static QueueEventBroker local(QueueEventHub hub) {
        return hub::dispatch;
    }
}
