package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

public interface QueueRepository {

    Optional<Queue> findById(OrganizationId organizationId, QueueId queueId);

    /**
     * Carrega a fila bloqueando-a até o fim da transação (PostgreSQL: {@code FOR NO KEY UPDATE}), para
     * serializar operações que alteram seu estado de chamada (intercalação de prioridades).
     */
    Optional<Queue> lockById(OrganizationId organizationId, QueueId queueId);

    void save(Queue queue);
}
