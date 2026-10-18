package br.com.matheuslealpa.queueflow.queue.api;

import br.com.matheuslealpa.queueflow.queue.application.QueueView;

import java.util.List;
import java.util.UUID;

public record QueueResponse(UUID id,
                            UUID locationId,
                            String name,
                            String ticketPrefix,
                            String status,
                            Policy policy,
                            List<UUID> acceptedServices,
                            List<Classification> classifications) {

    /** @param maxConsecutivePriorityCalls intercalação (2 = duas prioritárias para uma normal); 0 = prioridade estrita */
    public record Policy(String ordering, long callTimeoutSeconds, int maxCallAttempts, String returnPosition,
                         boolean allowReturnFromService, int maxConsecutivePriorityCalls) {
    }

    public record Classification(String code, String label, int level, boolean restricted) {
    }

    static QueueResponse of(QueueView view) {
        return new QueueResponse(view.id(), view.locationId(), view.name(), view.ticketPrefix(), view.status().name(),
                new Policy(view.ordering().name(), view.callTimeout().toSeconds(), view.maxCallAttempts(),
                        view.returnPosition().name(), view.allowReturnFromService(),
                        view.maxConsecutivePriorityCalls()),
                view.acceptedServices(),
                view.classifications().stream()
                        .map(c -> new Classification(c.code(), c.label(), c.level(), c.restricted()))
                        .toList());
    }
}
