package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

/** O bloqueio da fila não é simulado: os testes de concorrência com intercalação rodam contra o PostgreSQL. */
public final class InMemoryQueueRepository extends InMemoryRepository<Queue, QueueId> implements QueueRepository {

    @Override
    public Optional<Queue> lockById(OrganizationId organizationId, QueueId queueId) {
        return findById(organizationId, queueId);
    }
}
