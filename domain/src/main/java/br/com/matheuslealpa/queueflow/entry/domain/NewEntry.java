package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;

/**
 * Dados solicitados para uma nova entrada. Todos os campos são opcionais; a prioridade é
 * informada apenas pelo código de classificação e resolvida pela fila.
 */
public record NewEntry(ServiceOfferingId serviceOfferingId,
                       String priorityClassification,
                       ExternalReference externalReference,
                       IdempotencyKey idempotencyKey) {

    public static NewEntry plain() {
        return new NewEntry(null, null, null, null);
    }
}
