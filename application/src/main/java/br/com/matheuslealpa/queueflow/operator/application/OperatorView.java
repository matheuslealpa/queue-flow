package br.com.matheuslealpa.queueflow.operator.application;

import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.util.List;
import java.util.UUID;

public record OperatorView(UUID id, String name, boolean active, List<String> permissions,
                           List<Assignment> assignments) {

    /** @param servicePointId nulo quando o operador pode atender a fila de qualquer ponto da unidade */
    public record Assignment(UUID queueId, UUID servicePointId) {
    }

    public static OperatorView of(Operator operator) {
        return new OperatorView(operator.id().value(), operator.name(), operator.isActive(),
                operator.permissions().stream().map(Permission::name).sorted().toList(),
                operator.assignments().stream()
                        .map(a -> new Assignment(a.queueId().value(),
                                a.restrictedServicePoint().map(ServicePointId::value).orElse(null)))
                        .toList());
    }
}
