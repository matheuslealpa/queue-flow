package br.com.matheuslealpa.queueflow.service.infrastructure;

import br.com.matheuslealpa.queueflow.service.domain.ServiceOffering;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingRepository;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.infrastructure.Jdbc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcServiceOfferingRepository implements ServiceOfferingRepository {

    private final JdbcClient jdbc;

    public JdbcServiceOfferingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ServiceOffering> findById(OrganizationId organizationId, ServiceOfferingId id) {
        return jdbc.sql("""
                        SELECT id, organization_id, code, name, active, version
                        FROM service_offering WHERE organization_id = :org AND id = :id
                        """)
                .param("org", organizationId.value())
                .param("id", id.value())
                .query((rs, n) -> ServiceOffering.restore(new ServiceOfferingId(Jdbc.uuid(rs, "id")),
                        new OrganizationId(Jdbc.uuid(rs, "organization_id")), rs.getString("code"),
                        rs.getString("name"), rs.getBoolean("active"), rs.getLong("version")))
                .optional();
    }

    @Override
    public void save(ServiceOffering service) {
        try {
            var rows = jdbc.sql("""
                            INSERT INTO service_offering (id, organization_id, code, name, active, version)
                            VALUES (:id, :org, :code, :name, :active, :version)
                            ON CONFLICT (id) DO UPDATE
                                SET name = excluded.name, active = excluded.active,
                                    version = service_offering.version + 1
                                WHERE service_offering.version = excluded.version
                                  AND service_offering.organization_id = excluded.organization_id
                            """)
                    .param("id", service.id().value())
                    .param("org", service.organizationId().value())
                    .param("code", service.code())
                    .param("name", service.name())
                    .param("active", service.isActive())
                    .param("version", service.version())
                    .update();
            Jdbc.requireWritten(rows, "Serviço", service.id());
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleViolationException("Já existe um serviço com o código " + service.code());
        }
    }
}
