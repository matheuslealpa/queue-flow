package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record EntryCallMissed(
        OrganizationId organizationId,
        QueueEntryId entryId,
        QueueId queueId,
        MissedCallReason reason,
        int missedCalls,
        Instant occurredAt) implements DomainEvent {
}
