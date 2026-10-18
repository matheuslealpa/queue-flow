package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.DuplicateIdempotencyKeyException;
import br.com.matheuslealpa.queueflow.entry.domain.ExternalReference;
import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;
import br.com.matheuslealpa.queueflow.entry.domain.NewEntry;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntry;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.entry.domain.TicketNumber;
import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.Requester;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Registra uma pessoa em uma fila.
 *
 * <p>Idempotência: quando uma chave é informada, uma requisição repetida devolve a entrada
 * já criada, desde que fila, serviço e referência externa coincidam; caso contrário é um
 * conflito. Duas requisições simultâneas com a mesma chave são resolvidas pela restrição de
 * unicidade do repositório: a perdedora relê e devolve a entrada vencedora.
 */
public final class EnqueueEntry {

    public record Command(OrganizationId organizationId,
                          QueueId queueId,
                          ServiceOfferingId serviceOfferingId,
                          String priorityClassification,
                          ExternalReference externalReference,
                          IdempotencyKey idempotencyKey,
                          Requester requestedBy) {

        public Command {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(queueId, "queueId");
            Objects.requireNonNull(requestedBy, "requestedBy");
        }
    }

    private final UnitOfWork unitOfWork;
    private final QueueRepository queues;
    private final QueueEntryRepository entries;
    private final LocationRepository locations;
    private final ServiceOfferingRepository services;
    private final TicketSequence ticketSequence;
    private final ActorResolver actors;
    private final DomainEventPublisher events;
    private final Clock clock;

    public EnqueueEntry(UnitOfWork unitOfWork, QueueRepository queues, QueueEntryRepository entries,
                        LocationRepository locations, ServiceOfferingRepository services,
                        TicketSequence ticketSequence, ActorResolver actors, DomainEventPublisher events,
                        Clock clock) {
        this.unitOfWork = unitOfWork;
        this.queues = queues;
        this.entries = entries;
        this.locations = locations;
        this.services = services;
        this.ticketSequence = ticketSequence;
        this.actors = actors;
        this.events = events;
        this.clock = clock;
    }

    public EntryView handle(Command command) {
        try {
            return unitOfWork.execute(() -> findReplay(command).orElseGet(() -> create(command)));
        } catch (DuplicateIdempotencyKeyException concurrentCreation) {
            return unitOfWork.execute(() -> findReplay(command).orElseThrow(() -> concurrentCreation));
        }
    }

    private Optional<EntryView> findReplay(Command command) {
        if (command.idempotencyKey() == null) {
            return Optional.empty();
        }
        return entries.findByIdempotencyKey(command.organizationId(), command.queueId(), command.idempotencyKey())
                .map(existing -> {
                    var sameRequest = Objects.equals(existing.serviceOfferingId().orElse(null), command.serviceOfferingId())
                            && Objects.equals(existing.externalReference().orElse(null), command.externalReference());
                    if (!sameRequest) {
                        throw new IdempotencyConflictException(command.idempotencyKey());
                    }
                    return EntryView.of(existing);
                });
    }

    private EntryView create(Command command) {
        var org = command.organizationId();
        var queue = queues.findById(org, command.queueId())
                .orElseThrow(() -> new ResourceNotFoundException("Fila", command.queueId()));
        if (command.serviceOfferingId() != null) {
            var service = services.findById(org, command.serviceOfferingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Serviço", command.serviceOfferingId()));
            if (!service.isActive()) {
                throw new BusinessRuleViolationException("O serviço '%s' está inativo".formatted(service.name()));
            }
        }
        var location = locations.findById(org, queue.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade", queue.locationId()));
        var actor = actors.resolve(org, command.requestedBy());
        var now = clock.instant();

        queue.ensureAcceptsEntry(command.serviceOfferingId());
        var operationalDate = location.operationalDate(now);
        var ticket = new TicketNumber(queue.ticketPrefix(), ticketSequence.next(queue.id(), operationalDate),
                operationalDate);
        var request = new NewEntry(command.serviceOfferingId(), command.priorityClassification(),
                command.externalReference(), command.idempotencyKey());
        var entry = QueueEntry.create(queue, request, ticket, actor, now);

        entries.save(entry);
        events.publish(entry.pullEvents());
        return EntryView.of(entry);
    }
}
