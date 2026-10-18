package br.com.matheuslealpa.queueflow.queue.application;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

public final class GetQueue {

    private final QueueRepository queues;

    public GetQueue(QueueRepository queues) {
        this.queues = queues;
    }

    public QueueView handle(OrganizationId organizationId, QueueId queueId) {
        return queues.findById(organizationId, queueId)
                .map(QueueView::of)
                .orElseThrow(() -> new ResourceNotFoundException("Fila", queueId));
    }
}
