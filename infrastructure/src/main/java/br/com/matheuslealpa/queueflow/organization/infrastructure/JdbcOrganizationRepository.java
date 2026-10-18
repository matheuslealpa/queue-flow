package br.com.matheuslealpa.queueflow.organization.infrastructure;

import br.com.matheuslealpa.queueflow.organization.domain.Organization;
import br.com.matheuslealpa.queueflow.organization.domain.OrganizationRepository;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.infrastructure.Jdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    private final JdbcClient jdbc;

    public JdbcOrganizationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return jdbc.sql("SELECT id, name, active, version FROM organization WHERE id = :id")
                .param("id", id.value())
                .query((rs, n) -> Organization.restore(new OrganizationId(Jdbc.uuid(rs, "id")), rs.getString("name"),
                        rs.getBoolean("active"), rs.getLong("version")))
                .optional();
    }

    @Override
    public void save(Organization organization) {
        var rows = jdbc.sql("""
                        INSERT INTO organization (id, name, active, version)
                        VALUES (:id, :name, :active, :version)
                        ON CONFLICT (id) DO UPDATE
                            SET name = excluded.name, active = excluded.active, version = organization.version + 1
                            WHERE organization.version = excluded.version
                        """)
                .param("id", organization.id().value())
                .param("name", organization.name())
                .param("active", organization.isActive())
                .param("version", organization.version())
                .update();
        Jdbc.requireWritten(rows, "Organização", organization.id());
    }
}
