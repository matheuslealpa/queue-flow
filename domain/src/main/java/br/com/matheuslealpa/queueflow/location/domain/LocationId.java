package br.com.matheuslealpa.queueflow.location.domain;

import java.util.Objects;
import java.util.UUID;

public record LocationId(UUID value) {

    public LocationId {
        Objects.requireNonNull(value, "value");
    }

    public static LocationId newId() {
        return new LocationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
