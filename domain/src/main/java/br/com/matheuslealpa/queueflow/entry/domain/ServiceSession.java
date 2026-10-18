package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Execução de um atendimento. Uma entrada pode ter várias sessões quando é devolvida à fila;
 * o ciclo de vida da sessão (aberta/encerrada com um resultado) é independente do estado da entrada.
 */
public final class ServiceSession {

    private final ServiceSessionId id;
    private final OperatorId operatorId;
    private final ServicePointId servicePointId;
    private final Instant startedAt;
    private Instant endedAt;
    private SessionOutcome outcome;

    private ServiceSession(ServiceSessionId id, OperatorId operatorId, ServicePointId servicePointId,
                           Instant startedAt, Instant endedAt, SessionOutcome outcome) {
        this.id = Objects.requireNonNull(id, "id");
        this.operatorId = Objects.requireNonNull(operatorId, "operatorId");
        this.servicePointId = Objects.requireNonNull(servicePointId, "servicePointId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        if ((endedAt == null) != (outcome == null)) {
            throw new IllegalArgumentException("endedAt e outcome devem ser informados juntos");
        }
        this.endedAt = endedAt;
        this.outcome = outcome;
    }

    static ServiceSession open(OperatorId operatorId, ServicePointId servicePointId, Instant now) {
        return new ServiceSession(ServiceSessionId.newId(), operatorId, servicePointId, now, null, null);
    }

    public static ServiceSession restore(ServiceSessionId id, OperatorId operatorId, ServicePointId servicePointId,
                                         Instant startedAt, Instant endedAt, SessionOutcome outcome) {
        return new ServiceSession(id, operatorId, servicePointId, startedAt, endedAt, outcome);
    }

    void close(SessionOutcome result, Instant now) {
        if (!isOpen()) {
            throw new IllegalStateException("Sessão " + id + " já encerrada");
        }
        this.endedAt = now;
        this.outcome = Objects.requireNonNull(result, "result");
    }

    public boolean isOpen() {
        return outcome == null;
    }

    public Optional<Duration> duration() {
        return Optional.ofNullable(endedAt).map(end -> Duration.between(startedAt, end));
    }

    public ServiceSessionId id() {
        return id;
    }

    public OperatorId operatorId() {
        return operatorId;
    }

    public ServicePointId servicePointId() {
        return servicePointId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    public Optional<SessionOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }
}
