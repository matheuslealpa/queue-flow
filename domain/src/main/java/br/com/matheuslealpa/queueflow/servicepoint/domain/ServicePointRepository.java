package br.com.matheuslealpa.queueflow.servicepoint.domain;

import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

public interface ServicePointRepository {

    Optional<ServicePoint> findById(OrganizationId organizationId, ServicePointId id);

    void save(ServicePoint servicePoint);
}
