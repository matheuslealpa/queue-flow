package br.com.matheuslealpa.queueflow.operator.application;

import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.shared.application.Requester;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.Actor;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

/**
 * Converte o solicitante de uma requisição no {@link Actor} usado pelo domínio, com as
 * permissões vigentes do cadastro.
 */
public final class ActorResolver {

    private final OperatorRepository operators;

    public ActorResolver(OperatorRepository operators) {
        this.operators = operators;
    }

    public Actor resolve(OrganizationId organizationId, Requester requester) {
        return switch (requester) {
            case Requester.Staff staff -> operator(organizationId, staff.operatorId()).toActor();
            case Requester.SelfService selfService -> new Actor.SelfService(selfService.channel());
        };
    }

    public Operator operator(OrganizationId organizationId, OperatorId operatorId) {
        return operators.findById(organizationId, operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operador", operatorId));
    }

    public Actor.Staff staff(OrganizationId organizationId, OperatorId operatorId) {
        return operator(organizationId, operatorId).toActor();
    }

    public Actor.Staff staffWith(OrganizationId organizationId, OperatorId operatorId, Permission permission,
                                 String action) {
        var actor = staff(organizationId, operatorId);
        if (!actor.has(permission)) {
            throw new NotAuthorizedException(actor, action);
        }
        return actor;
    }
}
