package br.com.matheuslealpa.queueflow.location.domain;

import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

public interface LocationRepository {

    Optional<Location> findById(OrganizationId organizationId, LocationId id);

    void save(Location location);
}
