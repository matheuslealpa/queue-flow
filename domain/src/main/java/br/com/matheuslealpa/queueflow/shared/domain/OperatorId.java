package br.com.matheuslealpa.queueflow.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade de um operador. Fica no shared kernel porque {@link Actor} a referencia
 * e é usada por vários módulos para autorização e rastreabilidade.
 */
public record OperatorId(UUID value) {

    public OperatorId {
        Objects.requireNonNull(value, "value");
    }

    public static OperatorId newId() {
        return new OperatorId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
