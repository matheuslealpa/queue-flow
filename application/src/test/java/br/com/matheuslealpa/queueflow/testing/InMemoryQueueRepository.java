package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;

public final class InMemoryQueueRepository extends InMemoryRepository<Queue, QueueId> implements QueueRepository {
}
