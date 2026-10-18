package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.entry.domain.DuplicateIdempotencyKeyException;
import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntry;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.queue.domain.EntryOrdering;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Simula a semântica de {@code FOR UPDATE SKIP LOCKED}: entradas bloqueadas por outra
 * unidade de trabalho (thread) são ignoradas, e os bloqueios são liberados ao fim dela.
 * Valida a lógica dos casos de uso — não substitui o teste de integração com PostgreSQL.
 */
public final class InMemoryQueueEntryRepository extends InMemoryRepository<QueueEntry, QueueEntryId>
        implements QueueEntryRepository {

    private final Map<QueueEntryId, Thread> locks = new HashMap<>();

    @Override
    public Optional<QueueEntry> findByIdempotencyKey(OrganizationId organizationId, QueueId queueId,
                                                     IdempotencyKey key) {
        return store.values().stream()
                .filter(e -> e.belongsTo(organizationId) && e.queueId().equals(queueId))
                .filter(e -> e.idempotencyKey().filter(key::equals).isPresent())
                .findFirst();
    }

    @Override
    public synchronized Optional<QueueEntry> lockNextWaiting(OrganizationId organizationId, QueueId queueId,
                                                             EntryOrdering ordering) {
        var next = waiting(organizationId, queueId, ordering)
                .filter(e -> !locks.containsKey(e.id()) || locks.get(e.id()) == Thread.currentThread())
                .findFirst();
        next.ifPresent(e -> locks.put(e.id(), Thread.currentThread()));
        return next;
    }

    @Override
    public synchronized List<QueueEntry> findWaiting(OrganizationId organizationId, QueueId queueId,
                                                     EntryOrdering ordering, int offset, int limit) {
        return waiting(organizationId, queueId, ordering).skip(offset).limit(limit).toList();
    }

    @Override
    public synchronized List<QueueEntry> findExpiredCalls(Instant now, int limit) {
        return store.values().stream()
                .filter(e -> e.currentCall().filter(call -> call.isExpiredAt(now)).isPresent())
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized void save(QueueEntry entry) {
        var key = entry.idempotencyKey();
        if (key.isPresent()) {
            var duplicate = findByIdempotencyKey(entry.organizationId(), entry.queueId(), key.get())
                    .filter(existing -> !existing.id().equals(entry.id()));
            if (duplicate.isPresent()) {
                throw new DuplicateIdempotencyKeyException(entry.queueId(), key.get());
            }
        }
        super.save(entry);
    }

    synchronized void releaseLocksOf(Thread owner) {
        locks.values().removeIf(owner::equals);
    }

    private Stream<QueueEntry> waiting(OrganizationId organizationId, QueueId queueId, EntryOrdering ordering) {
        Comparator<QueueEntry> comparator = Comparator.comparing(QueueEntry::waitingPosition, ordering.comparator());
        return store.values().stream()
                .filter(e -> e.belongsTo(organizationId) && e.queueId().equals(queueId))
                .filter(e -> e.status() == EntryStatus.WAITING)
                .sorted(comparator);
    }
}
