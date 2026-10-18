package br.com.matheuslealpa.queueflow.entry.infrastructure;

import br.com.matheuslealpa.queueflow.entry.application.EntryView;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntry;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.queue.domain.EntrySelection;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CallNextConcurrencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired QueueEntryRepository entries;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcClient jdbc;

    @Test
    void sqlOrderingFollowsPriorityFifoPolicy() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.PRIORITY_FIFO);
        var operator = newAttendant(tenant, queue);
        var desk = newServicePoint(tenant, "Guichê 1");
        for (var classification : new String[]{null, "PREFERENCIAL", null, "PREFERENCIAL"}) {
            enqueue(tenant, queue, classification);
            clock.advance(Duration.ofSeconds(5));
        }

        var called = new ArrayList<String>();
        Optional<EntryView> next;
        while ((next = callNext(tenant, queue, operator, desk)).isPresent()) {
            called.add(next.get().ticket());
        }

        assertThat(called).containsExactly("A002", "A004", "A001", "A003");
    }

    @Test
    void sqlOrderingFollowsFifoPolicy() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var operator = newAttendant(tenant, queue);
        var desk = newServicePoint(tenant, "Guichê 1");
        enqueue(tenant, queue, null);
        enqueue(tenant, queue, "PREFERENCIAL");

        assertThat(callNext(tenant, queue, operator, desk).orElseThrow().ticket()).isEqualTo("A001");
        assertThat(callNext(tenant, queue, operator, desk).orElseThrow().ticket()).isEqualTo("A002");
    }

    /**
     * Prova determinística do SKIP LOCKED: enquanto a transação A mantém a primeira entrada
     * bloqueada, a transação B recebe imediatamente a segunda, sem esperar.
     */
    @Test
    void lockedHeadIsSkippedByConcurrentTransaction() throws Exception {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var first = enqueue(tenant, queue, null);
        clock.advance(Duration.ofSeconds(1));
        var second = enqueue(tenant, queue, null);
        var ordering = OrderingStrategy.FIFO.ordering();

        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = CompletableFuture.supplyAsync(() -> transactions.execute(status -> {
            var head = entries.lockNextWaiting(tenant.org(), queue, EntrySelection.any(ordering)).orElseThrow();
            locked.countDown();
            await(release);
            return head.id().value();
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        var concurrent = CompletableFuture.supplyAsync(() -> transactions.execute(status ->
                entries.lockNextWaiting(tenant.org(), queue, EntrySelection.any(ordering)).map(e -> e.id().value()).orElseThrow()));
        var concurrentId = concurrent.get(5, TimeUnit.SECONDS);
        release.countDown();

        assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo(first.id());
        assertThat(concurrentId).isEqualTo(second.id());
    }

    @Test
    void concurrentOperatorsNeverCallTheSameEntry() throws Exception {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.PRIORITY_FIFO);
        int totalEntries = 60;
        int operators = 12;
        for (int i = 0; i < totalEntries; i++) {
            enqueue(tenant, queue, i % 4 == 0 ? "PREFERENCIAL" : null);
        }
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<List<UUID>>>();
        for (int i = 0; i < operators; i++) {
            var operator = newAttendant(tenant, queue);
            var desk = newServicePoint(tenant, "Guichê " + i);
            tasks.add(() -> {
                start.await();
                var mine = new ArrayList<UUID>();
                Optional<EntryView> next;
                while ((next = callNext(tenant, queue, operator, desk)).isPresent()) {
                    mine.add(next.get().id());
                }
                return mine;
            });
        }

        var allCalled = new ArrayList<UUID>();
        try (var executor = Executors.newFixedThreadPool(operators)) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (var future : futures) {
                allCalled.addAll(future.get(60, TimeUnit.SECONDS));
            }
        }

        assertThat(allCalled).hasSize(totalEntries).doesNotHaveDuplicates();
        var calledInDb = jdbc.sql("""
                        SELECT count(*) FROM queue_entry
                        WHERE queue_id = :queue AND status = 'CALLED' AND call_operator_id IS NOT NULL
                        """)
                .param("queue", queue.value())
                .query(Long.class)
                .single();
        assertThat(calledInDb).isEqualTo(totalEntries);
        assertThat(entries.findWaiting(tenant.org(), queue, EntrySelection.any(OrderingStrategy.FIFO.ordering()), 0, 10))
                .extracting(QueueEntry::id)
                .isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
