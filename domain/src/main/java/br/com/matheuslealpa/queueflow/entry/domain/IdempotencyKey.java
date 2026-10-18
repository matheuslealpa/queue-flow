package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.shared.domain.Guards;

/**
 * Chave informada pelo cliente para tornar a criação de entradas idempotente. Única por fila.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        value = Guards.maxLength(Guards.notBlank(value, "idempotencyKey"), 100, "idempotencyKey");
    }
}
