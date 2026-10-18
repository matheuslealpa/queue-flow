package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record EntryCancelled(
        OrganizationId organizationId,
        QueueEntryId entryId,
        QueueId queueId,
        EntryStatus previousStatus,
        String reason,
        Instant occurredAt) implements DomainEvent {
}
