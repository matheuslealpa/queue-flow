package br.com.matheuslealpa.queueflow.reporting.application;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.LocalDate;

/**
 * Modelo de leitura dos indicadores, calculado diretamente pela persistência (sem carregar agregados).
 */
public interface QueueMetricsReader {

    QueueMetrics read(OrganizationId organizationId, QueueId queueId, LocalDate operationalDate);
}
