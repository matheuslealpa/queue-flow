package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

public interface QueueRepository {

    Optional<Queue> findById(OrganizationId organizationId, QueueId queueId);

    void save(Queue queue);
}
