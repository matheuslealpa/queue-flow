package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class QueueEventRelayIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    QueueEventHub hub;

    @Test
    void notifiesSubscribersOfTheQueueAfterCommit() throws InterruptedException {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var desk = newServicePoint(tenant, "Guichê 7");
        var attendant = newAttendant(tenant, queue);
        var otherQueue = newQueue(tenant, OrderingStrategy.FIFO);

        BlockingQueue<QueueEvent> received = new LinkedBlockingQueue<>();
        BlockingQueue<QueueEvent> otherReceived = new LinkedBlockingQueue<>();
        try (var _ = hub.subscribe(tenant.org(), queue, received::add);
             var _ = hub.subscribe(tenant.org(), otherQueue, otherReceived::add)) {
            var entry = enqueue(tenant, queue, null);
            callNext(tenant, queue, attendant, desk);

            var created = received.poll(5, TimeUnit.SECONDS);
            assertThat(created).isNotNull();
            assertThat(created.type()).isEqualTo("entry.created");
            assertThat(created.ticket()).isEqualTo(entry.ticket());

            var called = received.poll(5, TimeUnit.SECONDS);
            assertThat(called).isNotNull();
            assertThat(called.type()).isEqualTo("entry.called");
            assertThat(called.entryId()).isEqualTo(entry.id());
            assertThat(called.servicePointId()).isEqualTo(desk.value());
            assertThat(called.servicePointName()).isEqualTo("Guichê 7");
        }
        assertThat(otherReceived).isEmpty();
    }

    @Test
    void stopsDeliveringAfterTheSubscriptionIsClosed() throws InterruptedException {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        BlockingQueue<QueueEvent> received = new LinkedBlockingQueue<>();

        hub.subscribe(tenant.org(), queue, received::add).close();
        enqueue(tenant, queue, null);

        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }
}
