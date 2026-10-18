package br.com.matheuslealpa.queueflow.service.application;

import br.com.matheuslealpa.queueflow.service.domain.ServiceOffering;

import java.util.UUID;

public record ServiceOfferingView(UUID id, String code, String name, boolean active) {

    public static ServiceOfferingView of(ServiceOffering service) {
        return new ServiceOfferingView(service.id().value(), service.code(), service.name(), service.isActive());
    }
}
