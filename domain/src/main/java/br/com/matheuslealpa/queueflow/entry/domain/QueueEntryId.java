package br.com.matheuslealpa.queueflow.entry.domain;

import java.util.Objects;
import java.util.UUID;

public record QueueEntryId(UUID value) {

    public QueueEntryId {
        Objects.requireNonNull(value, "value");
    }

    public static QueueEntryId newId() {
        return new QueueEntryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
