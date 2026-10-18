package br.com.matheuslealpa.queueflow.service.api;

import br.com.matheuslealpa.queueflow.service.application.CreateServiceOffering;
import br.com.matheuslealpa.queueflow.service.application.ServiceOfferingView;
import br.com.matheuslealpa.queueflow.shared.api.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceOfferingController {

    public record CreateServiceRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 150) String name) {
    }

    private final CreateServiceOffering createServiceOffering;

    public ServiceOfferingController(CreateServiceOffering createServiceOffering) {
        this.createServiceOffering = createServiceOffering;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServiceOfferingView create(RequestContext context, @Valid @RequestBody CreateServiceRequest request) {
        return createServiceOffering.handle(new CreateServiceOffering.Command(context.organizationId(),
                context.requireOperator(), request.code(), request.name()));
    }
}
