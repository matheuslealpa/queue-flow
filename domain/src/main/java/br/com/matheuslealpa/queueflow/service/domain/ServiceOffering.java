package br.com.matheuslealpa.queueflow.service.domain;

import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Locale;

/**
 * Serviço que pode ser solicitado por uma pessoa (p.ex. "Retirada de medicamentos").
 * Nomeado ServiceOffering para não ser confundido com serviços de aplicação.
 */
public final class ServiceOffering extends AggregateRoot<ServiceOfferingId> {

    private final String code;
    private String name;
    private boolean active;

    private ServiceOffering(ServiceOfferingId id, OrganizationId organizationId, String code, String name,
                            boolean active, long version) {
        super(id, organizationId, version);
        this.code = Guards.maxLength(Guards.notBlank(code, "code"), 40, "code").toUpperCase(Locale.ROOT);
        this.name = Guards.maxLength(Guards.notBlank(name, "name"), 150, "name");
        this.active = active;
    }

    public static ServiceOffering create(OrganizationId organizationId, String code, String name) {
        return new ServiceOffering(ServiceOfferingId.newId(), organizationId, code, name, true, 0);
    }

    public static ServiceOffering restore(ServiceOfferingId id, OrganizationId organizationId, String code,
                                          String name, boolean active, long version) {
        return new ServiceOffering(id, organizationId, code, name, active, version);
    }

    public void rename(String newName) {
        this.name = Guards.maxLength(Guards.notBlank(newName, "name"), 150, "name");
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
