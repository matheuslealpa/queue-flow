package br.com.matheuslealpa.queueflow.operator.infrastructure;

import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorAssignment;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;
import br.com.matheuslealpa.queueflow.shared.infrastructure.Jdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOperatorRepository implements OperatorRepository {

    private final JdbcClient jdbc;

    public JdbcOperatorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(UUID id, UUID organizationId, String name, boolean active, long version) {
    }

    @Override
    public Optional<Operator> findById(OrganizationId organizationId, OperatorId id) {
        return jdbc.sql("""
                        SELECT id, organization_id, name, active, version
                        FROM operator WHERE organization_id = :org AND id = :id
                        """)
                .param("org", organizationId.value())
                .param("id", id.value())
                .query((rs, n) -> new Row(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "organization_id"),
                        rs.getString("name"), rs.getBoolean("active"), rs.getLong("version")))
                .optional()
                .map(this::restore);
    }

    private Operator restore(Row row) {
        var permissions = new HashSet<>(jdbc.sql("SELECT permission FROM operator_permission WHERE operator_id = :id")
                .param("id", row.id())
                .query((rs, n) -> Permission.valueOf(rs.getString("permission")))
                .list());
        var assignments = new HashSet<>(jdbc.sql("""
                        SELECT queue_id, service_point_id FROM operator_assignment WHERE operator_id = :id
                        """)
                .param("id", row.id())
                .query((rs, n) -> {
                    var servicePoint = Jdbc.uuid(rs, "service_point_id");
                    return new OperatorAssignment(new QueueId(Jdbc.uuid(rs, "queue_id")),
                            servicePoint == null ? null : new ServicePointId(servicePoint));
                })
                .list());
        return Operator.restore(new OperatorId(row.id()), new OrganizationId(row.organizationId()), row.name(),
                row.active(), permissions, assignments, row.version());
    }

    @Override
    public void save(Operator operator) {
        var id = operator.id().value();
        var rows = jdbc.sql("""
                        INSERT INTO operator (id, organization_id, name, active, version)
                        VALUES (:id, :org, :name, :active, :version)
                        ON CONFLICT (id) DO UPDATE
                            SET name = excluded.name, active = excluded.active, version = operator.version + 1
                            WHERE operator.version = excluded.version
                              AND operator.organization_id = excluded.organization_id
                        """)
                .param("id", id)
                .param("org", operator.organizationId().value())
                .param("name", operator.name())
                .param("active", operator.isActive())
                .param("version", operator.version())
                .update();
        Jdbc.requireWritten(rows, "Operador", operator.id());

        jdbc.sql("DELETE FROM operator_permission WHERE operator_id = :id").param("id", id).update();
        for (var permission : operator.permissions()) {
            jdbc.sql("INSERT INTO operator_permission (operator_id, permission) VALUES (:id, :permission)")
                    .param("id", id)
                    .param("permission", permission.name())
                    .update();
        }
        jdbc.sql("DELETE FROM operator_assignment WHERE operator_id = :id").param("id", id).update();
        for (var assignment : operator.assignments()) {
            jdbc.sql("""
                            INSERT INTO operator_assignment (operator_id, queue_id, service_point_id)
                            VALUES (:id, :queue, :servicePoint)
                            """)
                    .param("id", id)
                    .param("queue", assignment.queueId().value())
                    .param("servicePoint", assignment.restrictedServicePoint().map(ServicePointId::value).orElse(null))
                    .update();
        }
    }
}
