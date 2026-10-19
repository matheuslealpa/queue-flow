package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import java.util.UUID;

/** Nomes dos canais Redis de eventos de fila: {@code queueflow:queue-events:{organização}:{fila}}. */
final class QueueEventChannels {

    static final String PREFIX = "queueflow:queue-events:";
    static final String PATTERN = PREFIX + "*";

    private QueueEventChannels() {
    }

    static String of(UUID organizationId, UUID queueId) {
        return PREFIX + organizationId + ":" + queueId;
    }
}
