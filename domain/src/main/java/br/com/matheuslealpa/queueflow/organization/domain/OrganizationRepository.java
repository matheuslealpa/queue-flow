package br.com.matheuslealpa.queueflow.organization.domain;

import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

public interface OrganizationRepository {

    Optional<Organization> findById(OrganizationId id);

    void save(Organization organization);
}
