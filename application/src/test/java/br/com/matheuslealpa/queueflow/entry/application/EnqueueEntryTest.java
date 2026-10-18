package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.EntryCreated;
import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.entry.domain.ExternalReference;
import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.testing.QueueFlowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static br.com.matheuslealpa.queueflow.testing.QueueFlowFixture.KIOSK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnqueueEntryTest {

    private QueueFlowFixture fx;
    private Queue queue;

    @BeforeEach
    void setUp() {
        fx = new QueueFlowFixture();
        queue = fx.queue(OrderingStrategy.PRIORITY_FIFO);
    }

    private EnqueueEntry.Command command(String key, String externalReference) {
        return new EnqueueEntry.Command(fx.org, queue.id(), fx.service.id(), null,
                externalReference == null ? null : new ExternalReference(externalReference),
                key == null ? null : new IdempotencyKey(key), KIOSK);
    }

    @Test
    void createsWaitingEntryWithSequentialTicketsPerOperationalDay() {
        var first = fx.enqueue(queue);
        var second = fx.enqueue(queue, "PREFERENCIAL");

        assertThat(first.status()).isEqualTo(EntryStatus.WAITING);
        assertThat(first.ticket()).isEqualTo("A001");
        assertThat(second.ticket()).isEqualTo("A002");
        assertThat(second.priorityLevel()).isEqualTo(10);
        // 12:00Z corresponde a 09:00 em America/Sao_Paulo
        assertThat(first.operationalDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(fx.events.published(EntryCreated.class)).hasSize(2);
    }

    @Test
    void ticketNumberingRestartsOnNextOperationalDayInLocationTimeZone() {
        fx.enqueue(queue);
        fx.clock.advance(Duration.ofHours(13)); // 01:00Z do dia 19 = 22:00 do dia 18 em São Paulo
        assertThat(fx.enqueue(queue).ticket()).isEqualTo("A002");

        fx.clock.advance(Duration.ofHours(3)); // 01:00 do dia 19 em São Paulo
        var nextDay = fx.enqueue(queue);
        assertThat(nextDay.ticket()).isEqualTo("A001");
        assertThat(nextDay.operationalDate()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void sameIdempotencyKeyReturnsExistingEntry() {
        var first = fx.enqueueEntry.handle(command("req-1", "PED-1"));
        var replay = fx.enqueueEntry.handle(command("req-1", "PED-1"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.ticket()).isEqualTo("A001");
        assertThat(fx.entries.count()).isEqualTo(1);
        assertThat(fx.events.published(EntryCreated.class)).hasSize(1);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsConflict() {
        fx.enqueueEntry.handle(command("req-1", "PED-1"));

        assertThatThrownBy(() -> fx.enqueueEntry.handle(command("req-1", "PED-2")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void idempotencyKeyIsScopedToTheQueue() {
        var otherQueue = fx.queue(OrderingStrategy.FIFO);
        fx.enqueueEntry.handle(command("req-1", null));

        var inOtherQueue = fx.enqueueEntry.handle(new EnqueueEntry.Command(fx.org, otherQueue.id(), fx.service.id(),
                null, null, new IdempotencyKey("req-1"), KIOSK));

        assertThat(fx.entries.count()).isEqualTo(2);
        assertThat(inOtherQueue.queueId()).isEqualTo(otherQueue.id().value());
    }

    @Test
    void concurrentRequestsWithSameKeyCreateASingleEntry() throws Exception {
        int threads = 16;
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<EntryView>>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                start.await();
                return fx.enqueueEntry.handle(command("same-key", "PED-1"));
            });
        }
        try (var executor = Executors.newFixedThreadPool(threads)) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            var ids = new ArrayList<Object>();
            for (var future : futures) {
                ids.add(future.get().id());
            }
            assertThat(ids).containsOnly(ids.getFirst());
        }
        assertThat(fx.entries.count()).isEqualTo(1);
    }

    @Test
    void rejectsPausedQueue() {
        queue.pause(fx.clock.instant());

        assertThatThrownBy(() -> fx.enqueue(queue)).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(fx.entries.count()).isZero();
    }

    @Test
    void rejectsUnknownOrIncompatibleService() {
        assertThatThrownBy(() -> fx.enqueueEntry.handle(new EnqueueEntry.Command(fx.org, queue.id(),
                ServiceOfferingId.newId(), null, null, null, KIOSK)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsInactiveService() {
        fx.service.deactivate();

        assertThatThrownBy(() -> fx.enqueue(queue)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void selfServiceCannotRequestRestrictedPriority() {
        assertThatThrownBy(() -> fx.enqueue(queue, "URGENTE")).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void authorizedStaffCanAssignRestrictedPriority() {
        var entry = fx.enqueueEntry.handle(new EnqueueEntry.Command(fx.org, queue.id(), fx.service.id(), "URGENTE",
                null, null, fx.staff(fx.manager)));

        assertThat(entry.priorityClassification()).isEqualTo("URGENTE");
        assertThat(entry.priorityLevel()).isEqualTo(50);
    }

    @Test
    void queueOfAnotherOrganizationIsNotFound() {
        assertThatThrownBy(() -> fx.enqueueEntry.handle(new EnqueueEntry.Command(OrganizationId.newId(), queue.id(),
                fx.service.id(), null, null, null, KIOSK)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> fx.enqueueEntry.handle(new EnqueueEntry.Command(fx.org, QueueId.newId(),
                fx.service.id(), null, null, null, KIOSK)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
