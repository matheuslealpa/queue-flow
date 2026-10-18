package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainException;

/**
 * Lançada pelo repositório quando outra transação já gravou uma entrada com a mesma chave
 * de idempotência na fila (violação da restrição de unicidade).
 */
public class DuplicateIdempotencyKeyException extends DomainException {

    public DuplicateIdempotencyKeyException(QueueId queueId, IdempotencyKey key) {
        super("Já existe uma entrada com a chave de idempotência '%s' na fila %s".formatted(key.value(), queueId));
    }
}
