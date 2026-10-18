package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;

import java.time.LocalDate;

/**
 * Gera números de senha sequenciais por fila e dia operacional. A implementação deve ser
 * atômica sob concorrência (p.ex. {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}).
 */
@FunctionalInterface
public interface TicketSequence {

    long next(QueueId queueId, LocalDate operationalDate);
}
