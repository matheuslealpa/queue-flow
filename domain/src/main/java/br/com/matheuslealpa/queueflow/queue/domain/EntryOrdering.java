package br.com.matheuslealpa.queueflow.queue.domain;

import java.util.Comparator;
import java.util.List;

/**
 * Descrição declarativa da ordem de atendimento. A mesma descrição é avaliada em memória
 * ({@link #comparator()}) e traduzida para SQL pela infraestrutura, mantendo a política
 * separada do mecanismo de persistência.
 */
public record EntryOrdering(List<SortKey> keys) {

    public EntryOrdering {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("A ordenação precisa de ao menos um critério");
        }
        keys = List.copyOf(keys);
    }

    public Comparator<WaitingPosition> comparator() {
        return keys.stream()
                .map(SortKey::comparator)
                .reduce(Comparator::thenComparing)
                .orElseThrow();
    }
}
