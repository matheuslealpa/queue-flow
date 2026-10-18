package br.com.matheuslealpa.queueflow.organization.application;

import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.organization.domain.Organization;
import br.com.matheuslealpa.queueflow.organization.domain.OrganizationRepository;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Cadastra uma organização e seu primeiro operador administrador, que recebe todas as permissões.
 * Operação de plataforma: deve ser restrita a administradores quando houver autenticação.
 */
public final class RegisterOrganization {

    public record Command(String name, String adminName) {
    }

    public record Result(UUID organizationId, UUID adminOperatorId) {
    }

    private final UnitOfWork unitOfWork;
    private final OrganizationRepository organizations;
    private final OperatorRepository operators;

    public RegisterOrganization(UnitOfWork unitOfWork, OrganizationRepository organizations,
                                OperatorRepository operators) {
        this.unitOfWork = unitOfWork;
        this.organizations = organizations;
        this.operators = operators;
    }

    public Result handle(Command command) {
        return unitOfWork.execute(() -> {
            var organization = Organization.create(command.name());
            var admin = Operator.create(organization.id(), command.adminName(), EnumSet.allOf(Permission.class));
            organizations.save(organization);
            operators.save(admin);
            return new Result(organization.id().value(), admin.id().value());
        });
    }
}
