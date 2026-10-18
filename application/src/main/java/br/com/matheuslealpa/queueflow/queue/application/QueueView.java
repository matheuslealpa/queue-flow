package br.com.matheuslealpa.queueflow.queue.application;

import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatus;
import br.com.matheuslealpa.queueflow.queue.domain.ReturnPosition;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public record QueueView(UUID id,
                        UUID organizationId,
                        UUID locationId,
                        String name,
                        String ticketPrefix,
                        QueueStatus status,
                        OrderingStrategy ordering,
                        Duration callTimeout,
                        int maxCallAttempts,
                        ReturnPosition returnPosition,
                        boolean allowReturnFromService,
                        int maxConsecutivePriorityCalls,
                        List<UUID> acceptedServices,
                        List<Classification> classifications) {

    public record Classification(String code, String label, int level, boolean restricted) {
    }

    public static QueueView of(Queue queue) {
        var policy = queue.policy();
        return new QueueView(queue.id().value(), queue.organizationId().value(), queue.locationId().value(),
                queue.name(), queue.ticketPrefix(), queue.status(), policy.ordering(), policy.callTimeout(),
                policy.maxCallAttempts(), policy.returnPosition(), policy.allowReturnFromService(),
                policy.maxConsecutivePriorityCalls(),
                queue.acceptedServices().stream().map(ServiceOfferingId::value).sorted().toList(),
                queue.classifications().stream()
                        .map(c -> new Classification(c.code(), c.label(), c.level(), c.restricted()))
                        .toList());
    }
}
