package br.com.matheuslealpa.queueflow.organization.api;

import br.com.matheuslealpa.queueflow.organization.application.RegisterOrganization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de organizações. Sem autenticação por enquanto: deve ser restrito a administradores
 * da plataforma assim que houver um mecanismo de identidade.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    public record RegisterOrganizationRequest(@NotBlank @Size(max = 150) String name,
                                              @NotBlank @Size(max = 150) String adminName) {
    }

    private final RegisterOrganization registerOrganization;

    public OrganizationController(RegisterOrganization registerOrganization) {
        this.registerOrganization = registerOrganization;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RegisterOrganization.Result register(@Valid @RequestBody RegisterOrganizationRequest request) {
        return registerOrganization.handle(new RegisterOrganization.Command(request.name(), request.adminName()));
    }
}
