package br.com.matheuslealpa.queueflow.operator.application;

import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.util.Set;

public final class RegisterOperator {

    public record Command(OrganizationId organizationId, OperatorId requestedBy, String name,
                          Set<Permission> permissions) {
    }

    private final UnitOfWork unitOfWork;
    private final OperatorRepository operators;
    private final ActorResolver actors;

    public RegisterOperator(UnitOfWork unitOfWork, OperatorRepository operators, ActorResolver actors) {
        this.unitOfWork = unitOfWork;
        this.operators = operators;
        this.actors = actors;
    }

    public OperatorView handle(Command command) {
        return unitOfWork.execute(() -> {
            actors.staffWith(command.organizationId(), command.requestedBy(), Permission.MANAGE_ORGANIZATION,
                    "cadastrar operadores");
            var operator = Operator.create(command.organizationId(), command.name(), command.permissions());
            operators.save(operator);
            return OperatorView.of(operator);
        });
    }
}
