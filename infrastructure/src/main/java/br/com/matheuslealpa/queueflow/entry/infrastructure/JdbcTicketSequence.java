package br.com.matheuslealpa.queueflow.entry.infrastructure;

import br.com.matheuslealpa.queueflow.entry.application.TicketSequence;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Incremento atômico por UPSERT. A linha do contador fica bloqueada até o fim da transação,
 * então senhas não se repetem e uma transação desfeita não deixa lacunas.
 */
@Component
public class JdbcTicketSequence implements TicketSequence {

    private final JdbcClient jdbc;

    public JdbcTicketSequence(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long next(QueueId queueId, LocalDate operationalDate) {
        return jdbc.sql("""
                        INSERT INTO ticket_counter (queue_id, operational_date, last_value)
                        VALUES (:queue, :date, 1)
                        ON CONFLICT (queue_id, operational_date)
                            DO UPDATE SET last_value = ticket_counter.last_value + 1
                        RETURNING last_value
                        """)
                .param("queue", queueId.value())
                .param("date", operationalDate)
                .query(Long.class)
                .single();
    }
}
