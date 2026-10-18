package br.com.matheuslealpa.queueflow.servicepoint.infrastructure;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.infrastructure.Jdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcServicePointRepository implements ServicePointRepository {

    private final JdbcClient jdbc;

    public JdbcServicePointRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ServicePoint> findById(OrganizationId organizationId, ServicePointId id) {
        return jdbc.sql("""
                        SELECT id, organization_id, location_id, name, active, version
                        FROM service_point WHERE organization_id = :org AND id = :id
                        """)
                .param("org", organizationId.value())
                .param("id", id.value())
                .query((rs, n) -> ServicePoint.restore(new ServicePointId(Jdbc.uuid(rs, "id")),
                        new OrganizationId(Jdbc.uuid(rs, "organization_id")),
                        new LocationId(Jdbc.uuid(rs, "location_id")), rs.getString("name"),
                        rs.getBoolean("active"), rs.getLong("version")))
                .optional();
    }

    @Override
    public void save(ServicePoint servicePoint) {
        var rows = jdbc.sql("""
                        INSERT INTO service_point (id, organization_id, location_id, name, active, version)
                        VALUES (:id, :org, :location, :name, :active, :version)
                        ON CONFLICT (id) DO UPDATE
                            SET name = excluded.name, active = excluded.active, version = service_point.version + 1
                            WHERE service_point.version = excluded.version
                              AND service_point.organization_id = excluded.organization_id
                        """)
                .param("id", servicePoint.id().value())
                .param("org", servicePoint.organizationId().value())
                .param("location", servicePoint.locationId().value())
                .param("name", servicePoint.name())
                .param("active", servicePoint.isActive())
                .param("version", servicePoint.version())
                .update();
        Jdbc.requireWritten(rows, "Ponto de atendimento", servicePoint.id());
    }
}
