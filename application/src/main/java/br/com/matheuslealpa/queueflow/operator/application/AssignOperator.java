package br.com.matheuslealpa.queueflow.operator.application;

import br.com.matheuslealpa.queueflow.operator.domain.OperatorAssignment;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

/**
 * Autoriza um operador a atender uma fila, opcionalmente restrito a um ponto de atendimento.
 */
public final class AssignOperator {

    public record Command(OrganizationId organizationId, OperatorId requestedBy, OperatorId operatorId,
                          QueueId queueId, ServicePointId servicePointId) {
    }

    private final UnitOfWork unitOfWork;
    private final OperatorRepository operators;
    private final QueueRepository queues;
    private final ServicePointRepository servicePoints;
    private final ActorResolver actors;

    public AssignOperator(UnitOfWork unitOfWork, OperatorRepository operators, QueueRepository queues,
                          ServicePointRepository servicePoints, ActorResolver actors) {
        this.unitOfWork = unitOfWork;
        this.operators = operators;
        this.queues = queues;
        this.servicePoints = servicePoints;
        this.actors = actors;
    }

    public OperatorView handle(Command command) {
        return unitOfWork.execute(() -> {
            var org = command.organizationId();
            actors.staffWith(org, command.requestedBy(), Permission.MANAGE_ORGANIZATION, "atribuir operadores");
            var operator = actors.operator(org, command.operatorId());
            var queue = queues.findById(org, command.queueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fila", command.queueId()));
            if (command.servicePointId() != null) {
                var servicePoint = servicePoints.findById(org, command.servicePointId())
                        .orElseThrow(() -> new ResourceNotFoundException("Ponto de atendimento", command.servicePointId()));
                if (!servicePoint.locationId().equals(queue.locationId())) {
                    throw new BusinessRuleViolationException("O ponto de atendimento '%s' não pertence à unidade da fila"
                            .formatted(servicePoint.name()));
                }
            }
            operator.assign(new OperatorAssignment(queue.id(), command.servicePointId()));
            operators.save(operator);
            return OperatorView.of(operator);
        });
    }
}
