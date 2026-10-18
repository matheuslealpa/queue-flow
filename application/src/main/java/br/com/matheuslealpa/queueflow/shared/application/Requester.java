package br.com.matheuslealpa.queueflow.shared.application;

import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;

import java.util.Objects;

/**
 * Identificação de quem fez a requisição, como informada pela camada de entrada (já autenticada).
 * As permissões nunca vêm da requisição: são carregadas do cadastro do operador.
 */
public sealed interface Requester {

    record Staff(OperatorId operatorId) implements Requester {

        public Staff {
            Objects.requireNonNull(operatorId, "operatorId");
        }
    }

    record SelfService(String channel) implements Requester {

        public SelfService {
            Objects.requireNonNull(channel, "channel");
        }
    }
}
