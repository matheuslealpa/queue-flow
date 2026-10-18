package br.com.matheuslealpa.queueflow.servicepoint.domain;

import java.util.Objects;
import java.util.UUID;

public record ServicePointId(UUID value) {

    public ServicePointId {
        Objects.requireNonNull(value, "value");
    }

    public static ServicePointId newId() {
        return new ServicePointId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
