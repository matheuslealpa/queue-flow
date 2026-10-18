package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.EntryOrdering;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência das entradas. Todas as consultas são restritas a uma organização.
 */
public interface QueueEntryRepository {

    Optional<QueueEntry> findById(OrganizationId organizationId, QueueEntryId entryId);

    Optional<QueueEntry> findByIdempotencyKey(OrganizationId organizationId, QueueId queueId, IdempotencyKey key);

    /**
     * Seleciona e bloqueia a próxima entrada {@code WAITING} da fila segundo a ordenação
     * informada, sem carregar as demais entradas. Deve ser chamado dentro de uma transação;
     * entradas já bloqueadas por outra transação são ignoradas, de modo que operadores
     * concorrentes nunca recebam a mesma entrada (PostgreSQL: {@code FOR UPDATE SKIP LOCKED}).
     */
    Optional<QueueEntry> lockNextWaiting(OrganizationId organizationId, QueueId queueId, EntryOrdering ordering);

    /** Entradas da fila em espera, na ordem de atendimento. */
    List<QueueEntry> findWaiting(OrganizationId organizationId, QueueId queueId, EntryOrdering ordering,
                                 int offset, int limit);

    /** Entradas chamadas cujo prazo de comparecimento terminou, de qualquer organização. */
    List<QueueEntry> findExpiredCalls(Instant now, int limit);

    /**
     * Persiste a entrada respeitando a versão carregada (controle otimista).
     *
     * @throws DuplicateIdempotencyKeyException se a chave de idempotência já existir na fila
     */
    void save(QueueEntry entry);
}
