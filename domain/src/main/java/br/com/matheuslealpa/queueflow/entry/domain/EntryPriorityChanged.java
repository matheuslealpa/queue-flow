package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.Priority;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record EntryPriorityChanged(
        OrganizationId organizationId,
        QueueEntryId entryId,
        QueueId queueId,
        Priority previous,
        Priority current,
        Instant occurredAt) implements DomainEvent {
}
