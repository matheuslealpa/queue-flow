package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record QueueStatusChanged(OrganizationId organizationId, QueueId queueId, QueueStatus from, QueueStatus to,
                                 Instant occurredAt) implements DomainEvent {
}
