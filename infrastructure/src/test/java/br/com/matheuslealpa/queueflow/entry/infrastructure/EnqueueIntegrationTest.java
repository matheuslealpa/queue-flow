package br.com.matheuslealpa.queueflow.entry.infrastructure;

import br.com.matheuslealpa.queueflow.entry.application.EnqueueEntry;
import br.com.matheuslealpa.queueflow.entry.application.EntryView;
import br.com.matheuslealpa.queueflow.entry.application.IdempotencyConflictException;
import br.com.matheuslealpa.queueflow.entry.domain.ExternalReference;
import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnqueueIntegrationTest extends PostgresIntegrationTest {

    @Autowired JdbcClient jdbc;

    private long countEntries(QueueId queue) {
        return jdbc.sql("SELECT count(*) FROM queue_entry WHERE queue_id = :queue")
                .param("queue", queue.value())
                .query(Long.class)
                .single();
    }

    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        var start = new CountDownLatch(1);
        var results = new ArrayList<T>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (var future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
        }
        return results;
    }

    @Test
    void concurrentEnqueuesReceiveDistinctSequentialTickets() throws Exception {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);

        var created = runConcurrently(20, () -> enqueue(tenant, queue, null));

        assertThat(created).extracting(EntryView::ticket)
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 20)
                        .mapToObj("A%03d"::formatted).toList());
    }

    @Test
    void concurrentRequestsWithSameIdempotencyKeyCreateOneEntry() throws Exception {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var command = new EnqueueEntry.Command(tenant.org(), queue, tenant.service(), null,
                new ExternalReference("PED-1"), new IdempotencyKey("same-key"), KIOSK);

        var results = runConcurrently(16, () -> enqueueEntry.handle(command));

        assertThat(results).extracting(EntryView::id).containsOnly(results.getFirst().id());
        assertThat(countEntries(queue)).isEqualTo(1);
    }

    @Test
    void reusedKeyWithDifferentPayloadIsRejected() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        enqueueEntry.handle(new EnqueueEntry.Command(tenant.org(), queue, tenant.service(), null,
                new ExternalReference("PED-1"), new IdempotencyKey("k"), KIOSK));

        assertThatThrownBy(() -> enqueueEntry.handle(new EnqueueEntry.Command(tenant.org(), queue, tenant.service(),
                null, new ExternalReference("PED-2"), new IdempotencyKey("k"), KIOSK)))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(countEntries(queue)).isEqualTo(1);
    }
}
