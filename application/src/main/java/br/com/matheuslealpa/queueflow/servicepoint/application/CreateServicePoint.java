package br.com.matheuslealpa.queueflow.servicepoint.application;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

public final class CreateServicePoint {

    public record Command(OrganizationId organizationId, OperatorId requestedBy, LocationId locationId, String name) {
    }

    private final UnitOfWork unitOfWork;
    private final ServicePointRepository servicePoints;
    private final LocationRepository locations;
    private final ActorResolver actors;

    public CreateServicePoint(UnitOfWork unitOfWork, ServicePointRepository servicePoints,
                              LocationRepository locations, ActorResolver actors) {
        this.unitOfWork = unitOfWork;
        this.servicePoints = servicePoints;
        this.locations = locations;
        this.actors = actors;
    }

    public ServicePointView handle(Command command) {
        return unitOfWork.execute(() -> {
            var org = command.organizationId();
            actors.staffWith(org, command.requestedBy(), Permission.MANAGE_ORGANIZATION,
                    "cadastrar pontos de atendimento");
            var location = locations.findById(org, command.locationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unidade", command.locationId()));
            if (!location.isActive()) {
                throw new BusinessRuleViolationException("A unidade '%s' está inativa".formatted(location.name()));
            }
            var servicePoint = ServicePoint.create(org, location.id(), command.name());
            servicePoints.save(servicePoint);
            return ServicePointView.of(servicePoint);
        });
    }
}
