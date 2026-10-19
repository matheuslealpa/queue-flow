package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import br.com.matheuslealpa.queueflow.entry.domain.EntryCallMissed;
import br.com.matheuslealpa.queueflow.entry.domain.EntryCalled;
import br.com.matheuslealpa.queueflow.entry.domain.EntryCancelled;
import br.com.matheuslealpa.queueflow.entry.domain.EntryCreated;
import br.com.matheuslealpa.queueflow.entry.domain.EntryMarkedNoShow;
import br.com.matheuslealpa.queueflow.entry.domain.EntryPriorityChanged;
import br.com.matheuslealpa.queueflow.entry.domain.EntryReturnedToQueue;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.ServiceCompleted;
import br.com.matheuslealpa.queueflow.entry.domain.ServiceStarted;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicyChanged;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatusChanged;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;

import java.util.Optional;

/**
 * Traduz eventos de domínio para {@link QueueEvent}. Eventos sem fila (cadastros) ou sem
 * interessados em tempo real (criação da fila) não geram notificação.
 */
public class QueueEventMapper {

    private final ServicePointRepository servicePoints;

    public QueueEventMapper(ServicePointRepository servicePoints) {
        this.servicePoints = servicePoints;
    }

    public Optional<QueueEvent> map(DomainEvent event) {
        return Optional.ofNullable(switch (event) {
            case EntryCreated e -> entry("entry.created", e, e.queueId(), e.entryId(), e.ticket().display());
            case EntryCalled e -> called(e);
            case EntryCallMissed e -> entry("entry.call-missed", e, e.queueId(), e.entryId(), null);
            case EntryMarkedNoShow e -> entry("entry.no-show", e, e.queueId(), e.entryId(), null);
            case EntryReturnedToQueue e -> entry("entry.returned", e, e.queueId(), e.entryId(), null);
            case EntryCancelled e -> entry("entry.cancelled", e, e.queueId(), e.entryId(), null);
            case EntryPriorityChanged e -> entry("entry.priority-changed", e, e.queueId(), e.entryId(), null);
            case ServiceStarted e -> entry("entry.service-started", e, e.queueId(), e.entryId(), null);
            case ServiceCompleted e -> entry("entry.service-completed", e, e.queueId(), e.entryId(), null);
            case QueueStatusChanged e -> new QueueEvent("queue.status-changed", e.organizationId().value(),
                    e.queueId().value(), null, null, e.to().name(), null, null, e.occurredAt());
            case QueuePolicyChanged e -> new QueueEvent("queue.policy-changed", e.organizationId().value(),
                    e.queueId().value(), null, null, null, null, null, e.occurredAt());
            default -> null;
        });
    }

    private static QueueEvent entry(String type, DomainEvent event, QueueId queueId, QueueEntryId entryId,
                                    String ticket) {
        return new QueueEvent(type, event.organizationId().value(), queueId.value(), entryId.value(), ticket,
                null, null, null, event.occurredAt());
    }

    /** O painel anuncia "senha → guichê"; o nome vai junto para dispensar uma consulta por chamada. */
    private QueueEvent called(EntryCalled e) {
        var name = servicePoints.findById(e.organizationId(), e.servicePointId())
                .map(ServicePoint::name)
                .orElse(null);
        return new QueueEvent("entry.called", e.organizationId().value(), e.queueId().value(), e.entryId().value(),
                e.ticket().display(), null, e.servicePointId().value(), name, e.occurredAt());
    }
}
