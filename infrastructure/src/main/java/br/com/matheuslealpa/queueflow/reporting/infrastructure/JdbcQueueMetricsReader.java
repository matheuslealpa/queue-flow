package br.com.matheuslealpa.queueflow.reporting.infrastructure;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.reporting.application.QueueMetrics;
import br.com.matheuslealpa.queueflow.reporting.application.QueueMetricsReader;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Indicadores agregados em uma única consulta. Espera = criação até o início do primeiro
 * atendimento; atendimento = duração das sessões concluídas.
 */
@Component
public class JdbcQueueMetricsReader implements QueueMetricsReader {

    private final JdbcClient jdbc;

    public JdbcQueueMetricsReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueueMetrics read(OrganizationId organizationId, QueueId queueId, LocalDate operationalDate) {
        return jdbc.sql("""
                        SELECT
                            (SELECT count(*) FROM queue_entry
                              WHERE organization_id = :org AND queue_id = :queue AND status = 'WAITING') AS waiting,
                            (SELECT count(*) FROM queue_entry
                              WHERE organization_id = :org AND queue_id = :queue AND status = 'CALLED') AS called,
                            (SELECT count(*) FROM queue_entry
                              WHERE organization_id = :org AND queue_id = :queue AND status = 'IN_SERVICE') AS in_service,
                            count(*) FILTER (WHERE e.status = 'COMPLETED') AS completed,
                            count(*) FILTER (WHERE e.status = 'CANCELLED') AS cancelled,
                            count(*) FILTER (WHERE e.status = 'NO_SHOW') AS no_show,
                            avg(EXTRACT(EPOCH FROM (first_session.started_at - e.created_at)))::float8 AS avg_wait,
                            (SELECT avg(EXTRACT(EPOCH FROM (s.ended_at - s.started_at)))::float8
                               FROM service_session s JOIN queue_entry x ON x.id = s.entry_id
                              WHERE x.organization_id = :org AND x.queue_id = :queue
                                AND x.operational_date = :date AND s.outcome = 'COMPLETED') AS avg_service
                        FROM queue_entry e
                        LEFT JOIN LATERAL (
                            SELECT min(s.started_at) AS started_at FROM service_session s WHERE s.entry_id = e.id
                        ) first_session ON TRUE
                        WHERE e.organization_id = :org AND e.queue_id = :queue AND e.operational_date = :date
                        """)
                .param("org", organizationId.value())
                .param("queue", queueId.value())
                .param("date", operationalDate)
                .query((rs, n) -> new QueueMetrics(queueId.value(), operationalDate, rs.getLong("waiting"),
                        rs.getLong("called"), rs.getLong("in_service"), rs.getLong("completed"),
                        rs.getLong("cancelled"), rs.getLong("no_show"), rs.getObject("avg_wait", Double.class),
                        rs.getObject("avg_service", Double.class)))
                .single();
    }
}
