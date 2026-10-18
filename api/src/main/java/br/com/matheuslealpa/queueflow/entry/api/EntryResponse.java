package br.com.matheuslealpa.queueflow.entry.api;

import br.com.matheuslealpa.queueflow.entry.application.EntryView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EntryResponse(UUID id,
                            UUID queueId,
                            String ticket,
                            LocalDate operationalDate,
                            String status,
                            Priority priority,
                            UUID serviceId,
                            String externalReference,
                            Instant createdAt,
                            Instant enqueuedAt,
                            Call call,
                            int missedCalls,
                            int serviceSessions) {

    public record Priority(String classification, int level) {
    }

    public record Call(UUID operatorId, UUID servicePointId, Instant calledAt, Instant expiresAt) {
    }

    static EntryResponse of(EntryView view) {
        var call = view.call();
        return new EntryResponse(view.id(), view.queueId(), view.ticket(), view.operationalDate(),
                view.status().name(), new Priority(view.priorityClassification(), view.priorityLevel()),
                view.serviceOfferingId(), view.externalReference(), view.createdAt(), view.enqueuedAt(),
                call == null ? null : new Call(call.operatorId(), call.servicePointId(), call.calledAt(),
                        call.expiresAt()),
                view.missedCalls(), view.sessions());
    }
}
