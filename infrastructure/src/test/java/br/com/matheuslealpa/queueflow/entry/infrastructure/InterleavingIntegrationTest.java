package br.com.matheuslealpa.queueflow.entry.infrastructure;

import br.com.matheuslealpa.queueflow.entry.application.EntryView;
import br.com.matheuslealpa.queueflow.entry.application.ListWaitingEntries;
import br.com.matheuslealpa.queueflow.queue.application.CreateQueue;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InterleavingIntegrationTest extends PostgresIntegrationTest {

    @Autowired ListWaitingEntries listWaitingEntries;
    @Autowired QueueRepository queues;

    private QueueId interleavedQueue(Tenant tenant, int limit) {
        return new QueueId(createQueue.handle(new CreateQueue.Command(tenant.org(), tenant.admin(), tenant.location(),
                "Intercalada", "I", QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO).withInterleaving(limit),
                Set.of(tenant.service()),
                List.of(new PriorityClassification("PREFERENCIAL", "Preferencial", 10, false)))).id());
    }

    @Test
    void callsTwoPrioritizedThenOneRegularAndListsInThatOrder() {
        var tenant = newTenant();
        var queue = interleavedQueue(tenant, 2);
        var operator = newAttendant(tenant, queue);
        var desk = newServicePoint(tenant, "Guichê 1");
        for (var classification : new String[]{null, null, null, "PREFERENCIAL", "PREFERENCIAL", "PREFERENCIAL", "PREFERENCIAL"}) {
            enqueue(tenant, queue, classification);
            clock.advance(Duration.ofSeconds(5));
        }
        var projected = listWaitingEntries.handle(tenant.org(), queue, 0, 50).stream().map(EntryView::ticket).toList();

        var called = new ArrayList<String>();
        Optional<EntryView> next;
        while ((next = callNext(tenant, queue, operator, desk)).isPresent()) {
            called.add(next.get().ticket());
        }

        assertThat(called).containsExactly("I004", "I005", "I001", "I006", "I007", "I002", "I003");
        assertThat(projected).isEqualTo(called);
        assertThat(queues.findById(tenant.org(), queue).orElseThrow().consecutivePriorityCalls()).isZero();
    }

    /**
     * Com a fila bloqueada durante a chamada, operadores concorrentes produzem exatamente o padrão
     * P, P, N: 30 prioritárias e 15 normais terminam com o contador zerado e sem chamadas duplicadas.
     */
    @Test
    void concurrentCallsKeepInterleavingConsistent() throws Exception {
        var tenant = newTenant();
        var queue = interleavedQueue(tenant, 2);
        for (int i = 0; i < 45; i++) {
            enqueue(tenant, queue, i % 3 == 2 ? null : "PREFERENCIAL");
        }
        int operators = 10;
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<List<EntryView>>>();
        for (int i = 0; i < operators; i++) {
            var operator = newAttendant(tenant, queue);
            var desk = newServicePoint(tenant, "Guichê " + i);
            tasks.add(() -> {
                start.await();
                var mine = new ArrayList<EntryView>();
                Optional<EntryView> next;
                while ((next = callNext(tenant, queue, operator, desk)).isPresent()) {
                    mine.add(next.get());
                }
                return mine;
            });
        }

        var all = new ArrayList<EntryView>();
        try (var executor = Executors.newFixedThreadPool(operators)) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (var future : futures) {
                all.addAll(future.get(60, TimeUnit.SECONDS));
            }
        }

        assertThat(all).extracting(EntryView::id).hasSize(45).doesNotHaveDuplicates();
        assertThat(all).filteredOn(e -> e.priorityLevel() == 0).hasSize(15);
        assertThat(queues.findById(tenant.org(), queue).orElseThrow().consecutivePriorityCalls()).isZero();
    }
}
