package br.com.matheuslealpa.queueflow.reporting.application;

import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Clock;

/**
 * Indicadores da fila no dia operacional corrente da unidade.
 */
public final class GetQueueMetrics {

    private final QueueRepository queues;
    private final LocationRepository locations;
    private final QueueMetricsReader reader;
    private final Clock clock;

    public GetQueueMetrics(QueueRepository queues, LocationRepository locations, QueueMetricsReader reader,
                           Clock clock) {
        this.queues = queues;
        this.locations = locations;
        this.reader = reader;
        this.clock = clock;
    }

    public QueueMetrics handle(OrganizationId organizationId, QueueId queueId) {
        var queue = queues.findById(organizationId, queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Fila", queueId));
        var location = locations.findById(organizationId, queue.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade", queue.locationId()));
        return reader.read(organizationId, queueId, location.operationalDate(clock.instant()));
    }
}
