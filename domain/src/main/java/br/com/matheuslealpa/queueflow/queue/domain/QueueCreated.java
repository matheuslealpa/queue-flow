package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record QueueCreated(OrganizationId organizationId, QueueId queueId, LocationId locationId, String name,
                           OrderingStrategy ordering, Instant occurredAt) implements DomainEvent {
}
