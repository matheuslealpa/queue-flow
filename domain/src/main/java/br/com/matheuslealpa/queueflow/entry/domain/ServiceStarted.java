package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;

public record ServiceStarted(
        OrganizationId organizationId,
        QueueEntryId entryId,
        QueueId queueId,
        ServiceSessionId sessionId,
        OperatorId operatorId,
        ServicePointId servicePointId,
        Instant occurredAt) implements DomainEvent {
}
