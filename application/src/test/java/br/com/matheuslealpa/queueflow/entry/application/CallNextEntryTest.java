package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.EntryCalled;
import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.location.domain.Location;
import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorAssignment;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;
import br.com.matheuslealpa.queueflow.testing.QueueFlowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallNextEntryTest {

    private QueueFlowFixture fx;
    private ServicePoint desk;

    @BeforeEach
    void setUp() {
        fx = new QueueFlowFixture();
        desk = fx.servicePoint("Guichê 1");
    }

    private Optional<EntryView> callNext(Queue queue, Operator operator, ServicePoint servicePoint) {
        return fx.callNext.handle(new CallNextEntry.Command(fx.org, queue.id(), operator.id(), servicePoint.id()));
    }

    private List<String> drain(Queue queue, Operator operator) {
        var called = new ArrayList<String>();
        Optional<EntryView> next;
        while ((next = callNext(queue, operator, desk)).isPresent()) {
            called.add(next.get().ticket());
        }
        return called;
    }

    /** Cria entradas com chegadas espaçadas: A001 normal, A002 preferencial, A003 normal, A004 preferencial. */
    private void enqueueMixed(Queue queue) {
        for (var classification : new String[]{null, "PREFERENCIAL", null, "PREFERENCIAL"}) {
            fx.enqueue(queue, classification);
            fx.clock.advance(Duration.ofSeconds(30));
        }
    }

    @Test
    void fifoCallsByArrival() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        enqueueMixed(queue);

        assertThat(drain(queue, fx.attendant(queue))).containsExactly("A001", "A002", "A003", "A004");
    }

    @Test
    void priorityFifoCallsHigherPriorityFirstThenByArrival() {
        var queue = fx.queue(OrderingStrategy.PRIORITY_FIFO);
        enqueueMixed(queue);

        assertThat(drain(queue, fx.attendant(queue))).containsExactly("A002", "A004", "A001", "A003");
    }

    @Test
    void callReservesEntryForOperatorAndServicePoint() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        var operator = fx.attendant(queue);
        fx.enqueue(queue);

        var called = callNext(queue, operator, desk).orElseThrow();

        assertThat(called.status()).isEqualTo(EntryStatus.CALLED);
        assertThat(called.call().operatorId()).isEqualTo(operator.id().value());
        assertThat(called.call().servicePointId()).isEqualTo(desk.id().value());
        assertThat(fx.events.published(EntryCalled.class)).hasSize(1);
    }

    @Test
    void emptyQueueReturnsNothing() {
        var queue = fx.queue(OrderingStrategy.FIFO);

        assertThat(callNext(queue, fx.attendant(queue), desk)).isEmpty();
    }

    @Test
    void pausedQueueStillDrainsExistingEntries() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        fx.enqueue(queue);
        queue.pause(fx.clock.instant());

        assertThat(callNext(queue, fx.attendant(queue), desk)).isPresent();
    }

    @Test
    void operatorNotAssignedToQueueCannotCall() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        var otherQueue = fx.queue(OrderingStrategy.FIFO);
        fx.enqueue(queue);

        assertThatThrownBy(() -> callNext(queue, fx.attendant(otherQueue), desk))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void operatorRestrictedToAnotherServicePointCannotCall() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        var operator = Operator.create(fx.org, "Restrito", Set.of(Permission.CALL_ENTRIES));
        operator.assign(new OperatorAssignment(queue.id(), fx.servicePoint("Guichê 2").id()));
        fx.operators.save(operator);
        fx.enqueue(queue);

        assertThatThrownBy(() -> callNext(queue, operator, desk)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void inactiveOperatorCannotCall() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        var operator = fx.attendant(queue);
        operator.deactivate();
        fx.enqueue(queue);

        assertThatThrownBy(() -> callNext(queue, operator, desk)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void servicePointMustBelongToQueueLocation() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        var otherLocation = Location.create(fx.org, "Filial", ZoneOffset.UTC);
        fx.locations.save(otherLocation);
        var remoteDesk = ServicePoint.create(fx.org, otherLocation.id(), "Guichê remoto");
        fx.servicePoints.save(remoteDesk);

        assertThatThrownBy(() -> callNext(queue, fx.attendant(queue), remoteDesk))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void cannotCallQueueOfAnotherOrganization() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        var operator = fx.attendant(queue);
        fx.enqueue(queue);

        assertThatThrownBy(() -> fx.callNext.handle(new CallNextEntry.Command(OrganizationId.newId(), queue.id(),
                operator.id(), desk.id()))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concurrentOperatorsNeverCallTheSameEntry() throws Exception {
        var queue = fx.queue(OrderingStrategy.PRIORITY_FIFO);
        int entries = 40;
        int operators = 12;
        for (int i = 0; i < entries; i++) {
            fx.enqueue(queue, i % 3 == 0 ? "PREFERENCIAL" : null);
        }
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<List<UUID>>>();
        for (int i = 0; i < operators; i++) {
            var operator = fx.attendant(queue);
            var servicePoint = fx.servicePoint("Guichê " + (i + 10));
            tasks.add(() -> {
                start.await();
                var mine = new ArrayList<UUID>();
                Optional<EntryView> next;
                while ((next = callNext(queue, operator, servicePoint)).isPresent()) {
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
                allCalled.addAll(future.get());
            }
        }

        assertThat(allCalled).hasSize(entries).doesNotHaveDuplicates();
        assertThat(fx.events.published(EntryCalled.class)).hasSize(entries);
    }
}
