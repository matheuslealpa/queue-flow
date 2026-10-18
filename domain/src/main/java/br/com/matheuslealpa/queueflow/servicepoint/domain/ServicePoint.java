package br.com.matheuslealpa.queueflow.servicepoint.domain;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

/**
 * Ponto físico ou lógico de atendimento (guichê, consultório, sala, canal virtual).
 */
public final class ServicePoint extends AggregateRoot<ServicePointId> {

    private final LocationId locationId;
    private String name;
    private boolean active;

    private ServicePoint(ServicePointId id, OrganizationId organizationId, LocationId locationId, String name,
                         boolean active, long version) {
        super(id, organizationId, version);
        this.locationId = Guards.required(locationId, "locationId");
        this.name = Guards.maxLength(Guards.notBlank(name, "name"), 100, "name");
        this.active = active;
    }

    public static ServicePoint create(OrganizationId organizationId, LocationId locationId, String name) {
        return new ServicePoint(ServicePointId.newId(), organizationId, locationId, name, true, 0);
    }

    public static ServicePoint restore(ServicePointId id, OrganizationId organizationId, LocationId locationId,
                                       String name, boolean active, long version) {
        return new ServicePoint(id, organizationId, locationId, name, active, version);
    }

    public void rename(String newName) {
        this.name = Guards.maxLength(Guards.notBlank(newName, "name"), 100, "name");
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public LocationId locationId() {
        return locationId;
    }

    public String name() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
