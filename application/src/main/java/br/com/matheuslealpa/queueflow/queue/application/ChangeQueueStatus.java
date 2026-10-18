package br.com.matheuslealpa.queueflow.queue.application;

import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatus;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.time.Clock;

public final class ChangeQueueStatus {

    public record Command(OrganizationId organizationId, OperatorId requestedBy, QueueId queueId,
                          QueueStatus target) {
    }

    private final UnitOfWork unitOfWork;
    private final QueueRepository queues;
    private final ActorResolver actors;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ChangeQueueStatus(UnitOfWork unitOfWork, QueueRepository queues, ActorResolver actors,
                             DomainEventPublisher events, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.queues = queues;
        this.actors = actors;
        this.events = events;
        this.clock = clock;
    }

    public QueueView handle(Command command) {
        return unitOfWork.execute(() -> {
            var org = command.organizationId();
            actors.staffWith(org, command.requestedBy(), Permission.MANAGE_QUEUES, "alterar o status de filas");
            var queue = queues.findById(org, command.queueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fila", command.queueId()));
            var now = clock.instant();
            switch (command.target()) {
                case ACTIVE -> queue.activate(now);
                case PAUSED -> queue.pause(now);
                case CLOSED -> queue.close(now);
            }
            queues.save(queue);
            events.publish(queue.pullEvents());
            return QueueView.of(queue);
        });
    }
}
