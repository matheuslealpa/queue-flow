package br.com.matheuslealpa.queueflow.location.api;

import br.com.matheuslealpa.queueflow.location.application.CreateLocation;
import br.com.matheuslealpa.queueflow.location.application.LocationView;
import br.com.matheuslealpa.queueflow.shared.api.RequestContext;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    /** @param timeZone identificador IANA, p.ex. {@code America/Sao_Paulo} */
    public record CreateLocationRequest(@NotBlank @Size(max = 150) String name, @NotBlank String timeZone) {
    }

    private final CreateLocation createLocation;

    public LocationController(CreateLocation createLocation) {
        this.createLocation = createLocation;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LocationView create(RequestContext context, @Valid @RequestBody CreateLocationRequest request) {
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timeZone());
        } catch (DateTimeException e) {
            throw new BusinessRuleViolationException("Fuso horário inválido: " + request.timeZone());
        }
        return createLocation.handle(new CreateLocation.Command(context.organizationId(), context.requireOperator(),
                request.name(), zone));
    }
}
