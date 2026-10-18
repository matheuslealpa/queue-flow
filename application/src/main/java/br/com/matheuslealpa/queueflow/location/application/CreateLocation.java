package br.com.matheuslealpa.queueflow.location.application;

import br.com.matheuslealpa.queueflow.location.domain.Location;
import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.time.ZoneId;

public final class CreateLocation {

    public record Command(OrganizationId organizationId, OperatorId requestedBy, String name, ZoneId timeZone) {
    }

    private final UnitOfWork unitOfWork;
    private final LocationRepository locations;
    private final ActorResolver actors;

    public CreateLocation(UnitOfWork unitOfWork, LocationRepository locations, ActorResolver actors) {
        this.unitOfWork = unitOfWork;
        this.locations = locations;
        this.actors = actors;
    }

    public LocationView handle(Command command) {
        return unitOfWork.execute(() -> {
            actors.staffWith(command.organizationId(), command.requestedBy(), Permission.MANAGE_ORGANIZATION,
                    "cadastrar unidades");
            var location = Location.create(command.organizationId(), command.name(), command.timeZone());
            locations.save(location);
            return LocationView.of(location);
        });
    }
}
