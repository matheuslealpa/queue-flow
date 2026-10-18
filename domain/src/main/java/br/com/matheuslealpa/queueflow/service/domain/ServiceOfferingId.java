package br.com.matheuslealpa.queueflow.service.domain;

import java.util.Objects;
import java.util.UUID;

public record ServiceOfferingId(UUID value) {

    public ServiceOfferingId {
        Objects.requireNonNull(value, "value");
    }

    public static ServiceOfferingId newId() {
        return new ServiceOfferingId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
