package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.entry.application.TicketSequence;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryTicketSequence implements TicketSequence {

    private record Key(QueueId queueId, LocalDate operationalDate) {
    }

    private final Map<Key, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long next(QueueId queueId, LocalDate operationalDate) {
        return counters.computeIfAbsent(new Key(queueId, operationalDate), k -> new AtomicLong()).incrementAndGet();
    }
}
