package br.com.matheuslealpa.queueflow.servicepoint.application;

import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;

import java.util.UUID;

public record ServicePointView(UUID id, UUID locationId, String name, boolean active) {

    public static ServicePointView of(ServicePoint servicePoint) {
        return new ServicePointView(servicePoint.id().value(), servicePoint.locationId().value(),
                servicePoint.name(), servicePoint.isActive());
    }
}
