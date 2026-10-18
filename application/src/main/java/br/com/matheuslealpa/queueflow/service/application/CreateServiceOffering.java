package br.com.matheuslealpa.queueflow.service.application;

import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOffering;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingRepository;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

public final class CreateServiceOffering {

    public record Command(OrganizationId organizationId, OperatorId requestedBy, String code, String name) {
    }

    private final UnitOfWork unitOfWork;
    private final ServiceOfferingRepository services;
    private final ActorResolver actors;

    public CreateServiceOffering(UnitOfWork unitOfWork, ServiceOfferingRepository services, ActorResolver actors) {
        this.unitOfWork = unitOfWork;
        this.services = services;
        this.actors = actors;
    }

    public ServiceOfferingView handle(Command command) {
        return unitOfWork.execute(() -> {
            actors.staffWith(command.organizationId(), command.requestedBy(), Permission.MANAGE_ORGANIZATION,
                    "cadastrar serviços");
            var service = ServiceOffering.create(command.organizationId(), command.code(), command.name());
            services.save(service);
            return ServiceOfferingView.of(service);
        });
    }
}
