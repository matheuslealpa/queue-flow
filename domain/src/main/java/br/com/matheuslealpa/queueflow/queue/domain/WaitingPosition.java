package br.com.matheuslealpa.queueflow.queue.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Dados de uma entrada em espera relevantes para ordenação. Permite que a política de
 * ordenação seja definida pela fila sem depender do agregado de entradas.
 */
public record WaitingPosition(int priorityLevel, Instant enqueuedAt, long ticketSequence) {

    public WaitingPosition {
        Objects.requireNonNull(enqueuedAt, "enqueuedAt");
    }
}
