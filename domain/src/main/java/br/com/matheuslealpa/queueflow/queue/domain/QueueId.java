package br.com.matheuslealpa.queueflow.queue.domain;

import java.util.Objects;
import java.util.UUID;

public record QueueId(UUID value) {

    public QueueId {
        Objects.requireNonNull(value, "value");
    }

    public static QueueId newId() {
        return new QueueId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
