package br.com.matheuslealpa.queueflow.entry.domain;

import java.util.Objects;
import java.util.UUID;

public record ServiceSessionId(UUID value) {

    public ServiceSessionId {
        Objects.requireNonNull(value, "value");
    }

    public static ServiceSessionId newId() {
        return new ServiceSessionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
