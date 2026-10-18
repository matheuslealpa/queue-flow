package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;

import java.time.Instant;
import java.util.Objects;

/**
 * Chamada em andamento: quem chamou, para onde a pessoa deve se dirigir e até quando.
 */
public record CurrentCall(OperatorId operatorId, ServicePointId servicePointId, Instant calledAt, Instant expiresAt) {

    public CurrentCall {
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(servicePointId, "servicePointId");
        Objects.requireNonNull(calledAt, "calledAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
