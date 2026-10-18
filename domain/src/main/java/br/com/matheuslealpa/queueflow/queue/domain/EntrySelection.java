package br.com.matheuslealpa.queueflow.queue.domain;

import java.util.Objects;

/**
 * Critério para selecionar entradas em espera: ordem de atendimento e faixa de prioridade.
 * A persistência traduz para {@code WHERE}/{@code ORDER BY}; o domínio decide qual usar.
 */
public record EntrySelection(EntryOrdering ordering, PriorityBand band) {

    public EntrySelection {
        Objects.requireNonNull(ordering, "ordering");
        Objects.requireNonNull(band, "band");
    }

    public static EntrySelection any(EntryOrdering ordering) {
        return new EntrySelection(ordering, PriorityBand.ANY);
    }

    public boolean matches(WaitingPosition position) {
        return band.includes(position.priorityLevel());
    }
}
