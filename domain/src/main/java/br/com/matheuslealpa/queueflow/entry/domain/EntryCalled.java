package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record EntryCalled(
        OrganizationId organizationId,
        QueueEntryId entryId,
        QueueId queueId,
        TicketNumber ticket,
        OperatorId operatorId,
        ServicePointId servicePointId,
        Instant expiresAt,
        Instant occurredAt) implements DomainEvent {
}
