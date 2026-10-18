package br.com.matheuslealpa.queueflow.service.domain;

import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

public interface ServiceOfferingRepository {

    Optional<ServiceOffering> findById(OrganizationId organizationId, ServiceOfferingId id);

    void save(ServiceOffering serviceOffering);
}
