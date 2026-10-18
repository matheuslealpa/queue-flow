package br.com.matheuslealpa.queueflow.reporting.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Indicadores de uma fila. {@code waiting}, {@code called} e {@code inService} refletem o momento
 * atual; os demais se referem ao dia operacional informado. Médias são nulas sem amostras.
 */
public record QueueMetrics(UUID queueId,
                           LocalDate operationalDate,
                           long waiting,
                           long called,
                           long inService,
                           long completed,
                           long cancelled,
                           long noShow,
                           Double averageWaitSeconds,
                           Double averageServiceSeconds) {
}
