package br.com.matheuslealpa.queueflow.queue.domain;

import java.util.List;

import static br.com.matheuslealpa.queueflow.queue.domain.SortKey.ENQUEUED_AT_ASC;
import static br.com.matheuslealpa.queueflow.queue.domain.SortKey.PRIORITY_LEVEL_DESC;
import static br.com.matheuslealpa.queueflow.queue.domain.SortKey.TICKET_SEQUENCE_ASC;

/**
 * Estratégias de seleção suportadas. Extensões previstas (ainda sem requisitos concretos):
 * AGING (nível efetivo cresce com o tempo de espera) e WEIGHTED_PRIORITY (intercalação por peso).
 */
public enum OrderingStrategy {
    /** Estritamente por ordem de chegada; a prioridade é registrada mas ignorada. */
    FIFO(new EntryOrdering(List.of(ENQUEUED_AT_ASC, TICKET_SEQUENCE_ASC))),
    /** Prioridade descendente; dentro do mesmo nível, ordem de chegada. */
    PRIORITY_FIFO(new EntryOrdering(List.of(PRIORITY_LEVEL_DESC, ENQUEUED_AT_ASC, TICKET_SEQUENCE_ASC)));

    private final EntryOrdering ordering;

    OrderingStrategy(EntryOrdering ordering) {
        this.ordering = ordering;
    }

    public EntryOrdering ordering() {
        return ordering;
    }
}
