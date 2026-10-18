package br.com.matheuslealpa.queueflow.operator.api;

import br.com.matheuslealpa.queueflow.operator.application.AssignOperator;
import br.com.matheuslealpa.queueflow.operator.application.OperatorView;
import br.com.matheuslealpa.queueflow.operator.application.RegisterOperator;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.api.RequestContext;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operators")
public class OperatorController {

    public record RegisterOperatorRequest(@NotBlank @Size(max = 150) String name,
                                          @NotNull Set<Permission> permissions) {
    }

    /** @param servicePointId opcional; ausente permite atender a partir de qualquer ponto da unidade */
    public record AssignmentRequest(@NotNull UUID queueId, UUID servicePointId) {
    }

    private final RegisterOperator registerOperator;
    private final AssignOperator assignOperator;

    public OperatorController(RegisterOperator registerOperator, AssignOperator assignOperator) {
        this.registerOperator = registerOperator;
        this.assignOperator = assignOperator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OperatorView register(RequestContext context, @Valid @RequestBody RegisterOperatorRequest request) {
        return registerOperator.handle(new RegisterOperator.Command(context.organizationId(),
                context.requireOperator(), request.name(), request.permissions()));
    }

    @PostMapping("/{operatorId}/assignments")
    OperatorView assign(RequestContext context, @PathVariable UUID operatorId,
                        @Valid @RequestBody AssignmentRequest request) {
        return assignOperator.handle(new AssignOperator.Command(context.organizationId(), context.requireOperator(),
                new OperatorId(operatorId), new QueueId(request.queueId()),
                request.servicePointId() == null ? null : new ServicePointId(request.servicePointId())));
    }
}
