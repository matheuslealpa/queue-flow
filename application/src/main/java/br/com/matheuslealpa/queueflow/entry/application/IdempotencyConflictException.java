package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;

/**
 * A chave de idempotência já foi usada com uma requisição diferente.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(IdempotencyKey key) {
        super("A chave de idempotência '%s' já foi utilizada com dados diferentes".formatted(key.value()));
    }
}
