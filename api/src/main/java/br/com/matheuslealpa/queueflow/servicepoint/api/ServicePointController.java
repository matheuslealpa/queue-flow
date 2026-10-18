package br.com.matheuslealpa.queueflow.servicepoint.api;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.servicepoint.application.CreateServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.application.ServicePointView;
import br.com.matheuslealpa.queueflow.shared.api.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-points")
public class ServicePointController {

    public record CreateServicePointRequest(@NotNull UUID locationId, @NotBlank @Size(max = 100) String name) {
    }

    private final CreateServicePoint createServicePoint;

    public ServicePointController(CreateServicePoint createServicePoint) {
        this.createServicePoint = createServicePoint;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServicePointView create(RequestContext context, @Valid @RequestBody CreateServicePointRequest request) {
        return createServicePoint.handle(new CreateServicePoint.Command(context.organizationId(),
                context.requireOperator(), new LocationId(request.locationId()), request.name()));
    }
}
