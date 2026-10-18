package br.com.matheuslealpa.queueflow.queue.domain;

import java.util.Comparator;

/**
 * Critério de ordenação de entradas em espera. Adapters de persistência traduzem cada chave
 * para a coluna/direção correspondente (p.ex. {@code ORDER BY priority_level DESC}).
 */
public enum SortKey {
    PRIORITY_LEVEL_DESC(Comparator.comparingInt(WaitingPosition::priorityLevel).reversed()),
    ENQUEUED_AT_ASC(Comparator.comparing(WaitingPosition::enqueuedAt)),
    TICKET_SEQUENCE_ASC(Comparator.comparingLong(WaitingPosition::ticketSequence));

    private final Comparator<WaitingPosition> comparator;

    SortKey(Comparator<WaitingPosition> comparator) {
        this.comparator = comparator;
    }

    Comparator<WaitingPosition> comparator() {
        return comparator;
    }
}
