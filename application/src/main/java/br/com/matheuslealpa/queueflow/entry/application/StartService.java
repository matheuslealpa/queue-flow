package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Clock;

/**
 * A pessoa chamada se apresentou: inicia o atendimento com o operador que a chamou.
 */
public final class StartService {

    public record Command(OrganizationId organizationId, QueueEntryId entryId, OperatorId operatorId) {
    }

    private final EntryModifier modifier;
    private final ActorResolver actors;
    private final Clock clock;

    public StartService(UnitOfWork unitOfWork, QueueEntryRepository entries, QueueRepository queues,
                        ActorResolver actors, DomainEventPublisher events, Clock clock) {
        this.modifier = new EntryModifier(unitOfWork, entries, queues, events);
        this.actors = actors;
        this.clock = clock;
    }

    public EntryView handle(Command command) {
        return modifier.modify(command.organizationId(), command.entryId(), (entry, queue) -> {
            var actor = actors.staff(command.organizationId(), command.operatorId());
            entry.startService(actor, clock.instant());
        });
    }
}
