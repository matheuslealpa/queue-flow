package br.com.matheuslealpa.queueflow.queue.infrastructure;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatus;
import br.com.matheuslealpa.queueflow.queue.domain.ReturnPosition;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.infrastructure.Jdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcQueueRepository implements QueueRepository {

    private final JdbcClient jdbc;

    public JdbcQueueRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(UUID id, UUID organizationId, UUID locationId, String name, String ticketPrefix,
                       QueueStatus status, QueuePolicy policy, long version) {
    }

    @Override
    public Optional<Queue> findById(OrganizationId organizationId, QueueId queueId) {
        return jdbc.sql("""
                        SELECT id, organization_id, location_id, name, ticket_prefix, status, ordering,
                               call_timeout_seconds, max_call_attempts, return_position, allow_return_from_service,
                               version
                        FROM queue WHERE organization_id = :org AND id = :id
                        """)
                .param("org", organizationId.value())
                .param("id", queueId.value())
                .query((rs, n) -> new Row(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "organization_id"),
                        Jdbc.uuid(rs, "location_id"), rs.getString("name"), rs.getString("ticket_prefix"),
                        QueueStatus.valueOf(rs.getString("status")),
                        new QueuePolicy(OrderingStrategy.valueOf(rs.getString("ordering")),
                                Duration.ofSeconds(rs.getInt("call_timeout_seconds")), rs.getInt("max_call_attempts"),
                                ReturnPosition.valueOf(rs.getString("return_position")),
                                rs.getBoolean("allow_return_from_service")),
                        rs.getLong("version")))
                .optional()
                .map(this::restore);
    }

    private Queue restore(Row row) {
        var classifications = jdbc.sql("""
                        SELECT code, label, level, restricted FROM queue_priority_classification
                        WHERE queue_id = :id ORDER BY position
                        """)
                .param("id", row.id())
                .query((rs, n) -> new PriorityClassification(rs.getString("code"), rs.getString("label"),
                        rs.getInt("level"), rs.getBoolean("restricted")))
                .list();
        var services = new HashSet<>(jdbc.sql("SELECT service_offering_id FROM queue_accepted_service WHERE queue_id = :id")
                .param("id", row.id())
                .query((rs, n) -> new ServiceOfferingId(Jdbc.uuid(rs, "service_offering_id")))
                .list());
        return Queue.restore(new QueueId(row.id()), new OrganizationId(row.organizationId()),
                new LocationId(row.locationId()), row.name(), row.ticketPrefix(), row.status(), row.policy(),
                services, classifications, row.version());
    }

    @Override
    public void save(Queue queue) {
        var id = queue.id().value();
        var policy = queue.policy();
        var rows = jdbc.sql("""
                        INSERT INTO queue (id, organization_id, location_id, name, ticket_prefix, status, ordering,
                                           call_timeout_seconds, max_call_attempts, return_position,
                                           allow_return_from_service, version)
                        VALUES (:id, :org, :location, :name, :prefix, :status, :ordering, :timeout, :attempts,
                                :returnPosition, :allowReturn, :version)
                        ON CONFLICT (id) DO UPDATE
                            SET name = excluded.name, status = excluded.status, ordering = excluded.ordering,
                                call_timeout_seconds = excluded.call_timeout_seconds,
                                max_call_attempts = excluded.max_call_attempts,
                                return_position = excluded.return_position,
                                allow_return_from_service = excluded.allow_return_from_service,
                                version = queue.version + 1
                            WHERE queue.version = excluded.version AND queue.organization_id = excluded.organization_id
                        """)
                .param("id", id)
                .param("org", queue.organizationId().value())
                .param("location", queue.locationId().value())
                .param("name", queue.name())
                .param("prefix", queue.ticketPrefix())
                .param("status", queue.status().name())
                .param("ordering", policy.ordering().name())
                .param("timeout", Math.toIntExact(policy.callTimeout().toSeconds()))
                .param("attempts", policy.maxCallAttempts())
                .param("returnPosition", policy.returnPosition().name())
                .param("allowReturn", policy.allowReturnFromService())
                .param("version", queue.version())
                .update();
        Jdbc.requireWritten(rows, "Fila", queue.id());

        jdbc.sql("DELETE FROM queue_priority_classification WHERE queue_id = :id").param("id", id).update();
        var position = 0;
        for (var classification : queue.classifications()) {
            jdbc.sql("""
                            INSERT INTO queue_priority_classification (queue_id, code, label, level, restricted, position)
                            VALUES (:id, :code, :label, :level, :restricted, :position)
                            """)
                    .param("id", id)
                    .param("code", classification.code())
                    .param("label", classification.label())
                    .param("level", classification.level())
                    .param("restricted", classification.restricted())
                    .param("position", position++)
                    .update();
        }
        jdbc.sql("DELETE FROM queue_accepted_service WHERE queue_id = :id").param("id", id).update();
        for (var service : queue.acceptedServices()) {
            jdbc.sql("INSERT INTO queue_accepted_service (queue_id, service_offering_id) VALUES (:id, :service)")
                    .param("id", id)
                    .param("service", service.value())
                    .update();
        }
    }
}
