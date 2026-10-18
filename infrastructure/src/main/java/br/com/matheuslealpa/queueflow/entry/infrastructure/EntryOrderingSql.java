package br.com.matheuslealpa.queueflow.entry.infrastructure;

import br.com.matheuslealpa.queueflow.queue.domain.EntryOrdering;

import java.util.stream.Collectors;

/**
 * Traduz a ordenação declarada pelo domínio para {@code ORDER BY}. O {@code switch} exaustivo
 * força a atualização deste mapeamento quando uma nova chave de ordenação for criada.
 */
final class EntryOrderingSql {

    private EntryOrderingSql() {
    }

    static String orderBy(EntryOrdering ordering) {
        return ordering.keys().stream()
                .map(key -> switch (key) {
                    case PRIORITY_LEVEL_DESC -> "priority_level DESC";
                    case ENQUEUED_AT_ASC -> "enqueued_at ASC";
                    case TICKET_SEQUENCE_ASC -> "ticket_sequence ASC";
                })
                .collect(Collectors.joining(", ", "ORDER BY ", ", id"));
    }
}
