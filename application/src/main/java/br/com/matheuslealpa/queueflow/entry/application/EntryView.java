package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.CurrentCall;
import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.entry.domain.ExternalReference;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntry;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EntryView(UUID id,
                        UUID queueId,
                        String ticket,
                        LocalDate operationalDate,
                        EntryStatus status,
                        String priorityClassification,
                        int priorityLevel,
                        UUID serviceOfferingId,
                        String externalReference,
                        Instant createdAt,
                        Instant enqueuedAt,
                        Call call,
                        int missedCalls,
                        int sessions) {

    public record Call(UUID operatorId, UUID servicePointId, Instant calledAt, Instant expiresAt) {

        static Call of(CurrentCall call) {
            return new Call(call.operatorId().value(), call.servicePointId().value(), call.calledAt(),
                    call.expiresAt());
        }
    }

    public static EntryView of(QueueEntry entry) {
        return new EntryView(entry.id().value(), entry.queueId().value(), entry.ticket().display(),
                entry.ticket().operationalDate(), entry.status(), entry.priority().classificationCode(),
                entry.priority().level(), entry.serviceOfferingId().map(ServiceOfferingId::value).orElse(null),
                entry.externalReference().map(ExternalReference::value).orElse(null), entry.createdAt(),
                entry.enqueuedAt(), entry.currentCall().map(Call::of).orElse(null), entry.missedCalls(),
                entry.sessions().size());
    }
}
