package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.Requester;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Clock;

/**
 * Cancela uma entrada, por um operador autorizado ou pela própria pessoa enquanto aguarda.
 */
public final class CancelEntry {

    public record Command(OrganizationId organizationId, QueueEntryId entryId, Requester requestedBy, String reason) {
    }

    private final EntryModifier modifier;
    private final ActorResolver actors;
    private final Clock clock;

    public CancelEntry(UnitOfWork unitOfWork, QueueEntryRepository entries, QueueRepository queues,
                       ActorResolver actors, DomainEventPublisher events, Clock clock) {
        this.modifier = new EntryModifier(unitOfWork, entries, queues, events);
        this.actors = actors;
        this.clock = clock;
    }

    public EntryView handle(Command command) {
        return modifier.modify(command.organizationId(), command.entryId(), (entry, queue) -> {
            var actor = actors.resolve(command.organizationId(), command.requestedBy());
            entry.cancel(actor, command.reason(), clock.instant());
        });
    }
}
