package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record QueuePolicyChanged(OrganizationId organizationId, QueueId queueId, QueuePolicy policy,
                                 Instant occurredAt) implements DomainEvent {
}
