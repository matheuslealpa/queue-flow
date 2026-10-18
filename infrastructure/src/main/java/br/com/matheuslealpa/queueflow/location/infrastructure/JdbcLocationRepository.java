package br.com.matheuslealpa.queueflow.location.infrastructure;

import br.com.matheuslealpa.queueflow.location.domain.Location;
import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.infrastructure.Jdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.Optional;

@Repository
public class JdbcLocationRepository implements LocationRepository {

    private final JdbcClient jdbc;

    public JdbcLocationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Location> findById(OrganizationId organizationId, LocationId id) {
        return jdbc.sql("""
                        SELECT id, organization_id, name, time_zone, active, version
                        FROM location WHERE organization_id = :org AND id = :id
                        """)
                .param("org", organizationId.value())
                .param("id", id.value())
                .query((rs, n) -> Location.restore(new LocationId(Jdbc.uuid(rs, "id")),
                        new OrganizationId(Jdbc.uuid(rs, "organization_id")), rs.getString("name"),
                        ZoneId.of(rs.getString("time_zone")), rs.getBoolean("active"), rs.getLong("version")))
                .optional();
    }

    @Override
    public void save(Location location) {
        var rows = jdbc.sql("""
                        INSERT INTO location (id, organization_id, name, time_zone, active, version)
                        VALUES (:id, :org, :name, :timeZone, :active, :version)
                        ON CONFLICT (id) DO UPDATE
                            SET name = excluded.name, time_zone = excluded.time_zone, active = excluded.active,
                                version = location.version + 1
                            WHERE location.version = excluded.version
                              AND location.organization_id = excluded.organization_id
                        """)
                .param("id", location.id().value())
                .param("org", location.organizationId().value())
                .param("name", location.name())
                .param("timeZone", location.timeZone().getId())
                .param("active", location.isActive())
                .param("version", location.version())
                .update();
        Jdbc.requireWritten(rows, "Unidade", location.id());
    }
}
