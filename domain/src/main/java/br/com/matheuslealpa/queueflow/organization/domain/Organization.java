package br.com.matheuslealpa.queueflow.organization.domain;

import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

/**
 * Organização (tenant) que utiliza a plataforma.
 */
public final class Organization extends AggregateRoot<OrganizationId> {

    private String name;
    private boolean active;

    private Organization(OrganizationId id, String name, boolean active, long version) {
        super(id, id, version);
        this.name = Guards.maxLength(Guards.notBlank(name, "name"), 150, "name");
        this.active = active;
    }

    public static Organization create(String name) {
        return new Organization(OrganizationId.newId(), name, true, 0);
    }

    public static Organization restore(OrganizationId id, String name, boolean active, long version) {
        return new Organization(id, name, active, version);
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

    public String name() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
