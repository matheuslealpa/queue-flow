package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.Priority;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record EntryCreated(
        OrganizationId organizationId,
        QueueEntryId entryId,
        QueueId queueId,
        TicketNumber ticket,
        ServiceOfferingId serviceOfferingId,
        Priority priority,
        Instant occurredAt) implements DomainEvent {
}
