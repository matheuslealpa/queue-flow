package br.com.matheuslealpa.queueflow.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade do tenant. Todo agregado pertence a exatamente uma organização.
 */
public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "value");
    }

    public static OrganizationId newId() {
        return new OrganizationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
