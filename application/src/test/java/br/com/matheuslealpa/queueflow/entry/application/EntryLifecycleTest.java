package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.EntryCallMissed;
import br.com.matheuslealpa.queueflow.entry.domain.EntryCancelled;
import br.com.matheuslealpa.queueflow.entry.domain.EntryMarkedNoShow;
import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.ServiceCompleted;
import br.com.matheuslealpa.queueflow.entry.domain.ServiceStarted;
import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.ReturnPosition;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.InvalidStateTransitionException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.testing.QueueFlowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static br.com.matheuslealpa.queueflow.testing.QueueFlowFixture.KIOSK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryLifecycleTest {

    private QueueFlowFixture fx;
    private Queue queue;
    private Operator attendant;
    private ServicePoint desk;

    @BeforeEach
    void setUp() {
        fx = new QueueFlowFixture();
        queue = fx.queue(new QueuePolicy(OrderingStrategy.PRIORITY_FIFO, Duration.ofMinutes(2), 2,
                ReturnPosition.KEEP_ORIGINAL, true));
        attendant = fx.attendant(queue);
        desk = fx.servicePoint("Guichê 1");
    }

    private QueueEntryId id(EntryView view) {
        return new QueueEntryId(view.id());
    }

    private EntryView callNext() {
        return fx.callNext.handle(new CallNextEntry.Command(fx.org, queue.id(), attendant.id(), desk.id()))
                .orElseThrow();
    }

    @Test
    void fullServiceFlow() {
        var entry = fx.enqueue(queue);
        callNext();
        fx.clock.advance(Duration.ofSeconds(40));

        var started = fx.startService.handle(new StartService.Command(fx.org, id(entry), attendant.id()));
        fx.clock.advance(Duration.ofMinutes(4));
        var completed = fx.completeService.handle(new CompleteService.Command(fx.org, id(entry), attendant.id()));

        assertThat(started.status()).isEqualTo(EntryStatus.IN_SERVICE);
        assertThat(completed.status()).isEqualTo(EntryStatus.COMPLETED);
        assertThat(completed.sessions()).isEqualTo(1);
        assertThat(fx.events.published(ServiceStarted.class)).hasSize(1);
        assertThat(fx.events.published(ServiceCompleted.class)).singleElement()
                .extracting(ServiceCompleted::serviceDuration).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void anotherOperatorCannotStartServiceOfCalledEntry() {
        var entry = fx.enqueue(queue);
        callNext();
        var colleague = fx.attendant(queue);

        assertThatThrownBy(() -> fx.startService.handle(new StartService.Command(fx.org, id(entry), colleague.id())))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void cannotCompleteEntryThatWasNotStarted() {
        var entry = fx.enqueue(queue);

        assertThatThrownBy(() -> fx.completeService.handle(new CompleteService.Command(fx.org, id(entry),
                attendant.id()))).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void returnedEntryKeepsOriginalPositionAndIsCalledAgain() {
        var first = fx.enqueue(queue);
        fx.clock.advance(Duration.ofSeconds(10));
        fx.enqueue(queue);
        callNext();
        fx.startService.handle(new StartService.Command(fx.org, id(first), attendant.id()));

        var returned = fx.returnToQueue.handle(new ReturnEntryToQueue.Command(fx.org, id(first), attendant.id(),
                "aguardar resultado"));

        assertThat(returned.status()).isEqualTo(EntryStatus.WAITING);
        assertThat(callNext().id()).isEqualTo(first.id());
    }

    @Test
    void notPresentReturnsToQueueThenBecomesNoShow() {
        var entry = fx.enqueue(queue);

        callNext();
        var afterFirst = fx.registerNotPresent.handle(new RegisterNotPresent.Command(fx.org, id(entry), attendant.id()));
        callNext();
        var afterSecond = fx.registerNotPresent.handle(new RegisterNotPresent.Command(fx.org, id(entry), attendant.id()));

        assertThat(afterFirst.status()).isEqualTo(EntryStatus.WAITING);
        assertThat(afterSecond.status()).isEqualTo(EntryStatus.NO_SHOW);
        assertThat(afterSecond.missedCalls()).isEqualTo(2);
        assertThat(fx.events.published(EntryCallMissed.class)).hasSize(2);
        assertThat(fx.events.published(EntryMarkedNoShow.class)).hasSize(1);
    }

    @Test
    void expireCallsProcessesOnlyCallsPastTheirDeadline() {
        var expiring = fx.enqueue(queue);
        var recent = fx.enqueue(queue);
        callNext();
        fx.clock.advance(Duration.ofMinutes(1));
        callNext();

        fx.clock.advance(Duration.ofSeconds(70)); // primeira chamada passou de 2 min; segunda não
        var expired = fx.expireCalls.handle(50);

        assertThat(expired).isEqualTo(1);
        assertThat(fx.getEntry.handle(fx.org, id(expiring)).status()).isEqualTo(EntryStatus.WAITING);
        assertThat(fx.getEntry.handle(fx.org, id(recent)).status()).isEqualTo(EntryStatus.CALLED);
    }

    @Test
    void personMayGiveUpWhileWaiting() {
        var entry = fx.enqueue(queue);

        var cancelled = fx.cancelEntry.handle(new CancelEntry.Command(fx.org, id(entry), KIOSK, "desistiu"));

        assertThat(cancelled.status()).isEqualTo(EntryStatus.CANCELLED);
        assertThat(fx.events.published(EntryCancelled.class)).hasSize(1);
        assertThat(fx.listWaiting.handle(fx.org, queue.id(), 0, 10)).isEmpty();
    }

    @Test
    void cancellingCalledEntryRequiresPermission() {
        var entry = fx.enqueue(queue);
        callNext();

        assertThatThrownBy(() -> fx.cancelEntry.handle(new CancelEntry.Command(fx.org, id(entry), KIOSK, null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> fx.cancelEntry.handle(new CancelEntry.Command(fx.org, id(entry),
                fx.staff(attendant), null))).isInstanceOf(NotAuthorizedException.class);

        var cancelled = fx.cancelEntry.handle(new CancelEntry.Command(fx.org, id(entry), fx.staff(fx.manager),
                "encerramento do expediente"));
        assertThat(cancelled.status()).isEqualTo(EntryStatus.CANCELLED);
    }

    @Test
    void priorityChangeReordersWaitingList() {
        var first = fx.enqueue(queue);
        fx.clock.advance(Duration.ofSeconds(10));
        var second = fx.enqueue(queue);

        fx.changePriority.handle(new ChangeEntryPriority.Command(fx.org, id(second), fx.manager.id(), "PREFERENCIAL"));

        assertThat(fx.listWaiting.handle(fx.org, queue.id(), 0, 10)).extracting(EntryView::id)
                .containsExactly(second.id(), first.id());
    }

    @Test
    void priorityChangeRequiresPermission() {
        var entry = fx.enqueue(queue);

        assertThatThrownBy(() -> fx.changePriority.handle(new ChangeEntryPriority.Command(fx.org, id(entry),
                attendant.id(), "PREFERENCIAL"))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void entriesAreIsolatedBetweenOrganizations() {
        var entry = fx.enqueue(queue);
        var otherOrg = OrganizationId.newId();

        assertThatThrownBy(() -> fx.getEntry.handle(otherOrg, id(entry)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> fx.cancelEntry.handle(new CancelEntry.Command(otherOrg, id(entry), KIOSK, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> fx.listWaiting.handle(otherOrg, queue.id(), 0, 10))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(fx.getEntry.handle(fx.org, id(entry)).status()).isEqualTo(EntryStatus.WAITING);
    }
}
