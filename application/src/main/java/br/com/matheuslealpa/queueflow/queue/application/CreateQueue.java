package br.com.matheuslealpa.queueflow.queue.application;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.time.Clock;
import java.util.List;
import java.util.Set;

public final class CreateQueue {

    public record Command(OrganizationId organizationId,
                          OperatorId requestedBy,
                          LocationId locationId,
                          String name,
                          String ticketPrefix,
                          QueuePolicy policy,
                          Set<ServiceOfferingId> acceptedServices,
                          List<PriorityClassification> classifications) {
    }

    private final UnitOfWork unitOfWork;
    private final QueueRepository queues;
    private final LocationRepository locations;
    private final ServiceOfferingRepository services;
    private final ActorResolver actors;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CreateQueue(UnitOfWork unitOfWork, QueueRepository queues, LocationRepository locations,
                       ServiceOfferingRepository services, ActorResolver actors, DomainEventPublisher events,
                       Clock clock) {
        this.unitOfWork = unitOfWork;
        this.queues = queues;
        this.locations = locations;
        this.services = services;
        this.actors = actors;
        this.events = events;
        this.clock = clock;
    }

    public QueueView handle(Command command) {
        return unitOfWork.execute(() -> {
            var org = command.organizationId();
            actors.staffWith(org, command.requestedBy(), Permission.MANAGE_QUEUES, "criar filas");
            var location = locations.findById(org, command.locationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unidade", command.locationId()));
            if (!location.isActive()) {
                throw new BusinessRuleViolationException("A unidade '%s' está inativa".formatted(location.name()));
            }
            for (var serviceId : command.acceptedServices()) {
                var service = services.findById(org, serviceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Serviço", serviceId));
                if (!service.isActive()) {
                    throw new BusinessRuleViolationException("O serviço '%s' está inativo".formatted(service.name()));
                }
            }
            var queue = Queue.create(org, location.id(), command.name(), command.ticketPrefix(), command.policy(),
                    command.acceptedServices(), command.classifications(), clock.instant());
            queues.save(queue);
            events.publish(queue.pullEvents());
            return QueueView.of(queue);
        });
    }
}
