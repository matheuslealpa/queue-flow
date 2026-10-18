package br.com.matheuslealpa.queueflow.operator.domain;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;

import java.util.Objects;
import java.util.Optional;

/**
 * Autoriza um operador a atender uma fila. Sem ponto de atendimento definido, o
 * operador pode atender a fila a partir de qualquer ponto da mesma unidade.
 */
public record OperatorAssignment(QueueId queueId, ServicePointId servicePointId) {

    public OperatorAssignment {
        Objects.requireNonNull(queueId, "queueId");
    }

    public static OperatorAssignment anyServicePoint(QueueId queueId) {
        return new OperatorAssignment(queueId, null);
    }

    public Optional<ServicePointId> restrictedServicePoint() {
        return Optional.ofNullable(servicePointId);
    }

    public boolean covers(QueueId queue, ServicePointId servicePoint) {
        return queueId.equals(queue) && (servicePointId == null || servicePointId.equals(servicePoint));
    }
}
