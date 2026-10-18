package br.com.matheuslealpa.queueflow.shared.api;

import br.com.matheuslealpa.queueflow.shared.application.Requester;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

/**
 * Identidade da requisição. Enquanto não há autenticação, é lida dos cabeçalhos
 * {@value #ORGANIZATION_HEADER}, {@value #OPERATOR_HEADER} e {@value #CHANNEL_HEADER}; um
 * mecanismo real (p.ex. JWT) deve substituir apenas {@link RequestContextResolver}.
 */
public record RequestContext(OrganizationId organizationId, OperatorId operatorId, String channel) {

    public static final String ORGANIZATION_HEADER = "X-Organization-Id";
    public static final String OPERATOR_HEADER = "X-Operator-Id";
    public static final String CHANNEL_HEADER = "X-Client-Channel";

    /** Operador autenticado; exigido nas operações de atendimento e administração. */
    public OperatorId requireOperator() {
        if (operatorId == null) {
            throw new MissingIdentityException("Operação exige o cabeçalho " + OPERATOR_HEADER);
        }
        return operatorId;
    }

    /** Operador, se informado; caso contrário, o canal de autoatendimento. */
    public Requester requester() {
        if (operatorId != null) {
            return new Requester.Staff(operatorId);
        }
        if (channel != null) {
            return new Requester.SelfService(channel);
        }
        throw new MissingIdentityException("Informe %s ou %s".formatted(OPERATOR_HEADER, CHANNEL_HEADER));
    }
}
