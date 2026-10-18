package br.com.matheuslealpa.queueflow.shared.domain;

import java.time.Instant;

/**
 * Fato de negócio já ocorrido, emitido por um agregado.
 */
public interface DomainEvent {

    OrganizationId organizationId();

    Instant occurredAt();
}
