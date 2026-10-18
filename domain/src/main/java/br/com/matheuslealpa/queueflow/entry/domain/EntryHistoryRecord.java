package br.com.matheuslealpa.queueflow.entry.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de auditoria de uma operação relevante sobre a entrada. Persistido junto ao
 * agregado, independe da publicação de eventos.
 *
 * @param actor  identificação do ator ({@code Actor#describe()})
 * @param detail informação complementar (motivo, prioridade anterior etc.); pode ser nulo
 */
public record EntryHistoryRecord(EntryAction action, EntryStatus from, EntryStatus to, String actor, String detail,
                                 Instant occurredAt) {

    public EntryHistoryRecord {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
