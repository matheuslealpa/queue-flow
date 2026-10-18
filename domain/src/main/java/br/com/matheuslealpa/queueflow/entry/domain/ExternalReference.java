package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.shared.domain.Guards;

/**
 * Identificador da solicitação em um sistema externo integrado (prontuário, pedido, protocolo).
 * Opaco para o domínio.
 */
public record ExternalReference(String value) {

    public ExternalReference {
        value = Guards.maxLength(Guards.notBlank(value, "externalReference"), 200, "externalReference");
    }
}
