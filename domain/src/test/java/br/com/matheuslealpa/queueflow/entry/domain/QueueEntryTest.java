package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.Priority;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.ReturnPosition;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.Actor;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;
import br.com.matheuslealpa.queueflow.shared.domain.InvalidStateTransitionException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueEntryTest {

    private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");
    private static final ServiceOfferingId SERVICE = ServiceOfferingId.newId();
    private static final ServicePointId DESK = ServicePointId.newId();

    private final Actor kiosk = new Actor.SelfService("kiosk-1");
    private final Actor.Staff operator = new Actor.Staff(OperatorId.newId(), Set.of(Permission.CALL_ENTRIES));
    private final Actor.Staff otherOperator = new Actor.Staff(OperatorId.newId(), Set.of(Permission.CALL_ENTRIES));
    private final Actor.Staff supervisor = new Actor.Staff(OperatorId.newId(),
            Set.of(Permission.CHANGE_PRIORITY, Permission.CANCEL_ENTRY, Permission.ASSIGN_RESTRICTED_PRIORITY));

    private Queue queue;
    private long sequence;

    @BeforeEach
    void setUp() {
        queue = queueWith(QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO));
        sequence = 0;
    }

    private Queue queueWith(QueuePolicy policy) {
        return Queue.create(OrganizationId.newId(), LocationId.newId(), "Atendimento", "A", policy, Set.of(SERVICE),
                List.of(new PriorityClassification("PREFERENCIAL", "Preferencial", 10, false),
                        new PriorityClassification("URGENTE", "Urgente", 50, true)), T0);
    }

    private TicketNumber nextTicket() {
        return new TicketNumber("A", ++sequence, LocalDate.of(2026, 10, 18));
    }

    private QueueEntry newEntry() {
        var entry = QueueEntry.create(queue, new NewEntry(SERVICE, null, null, null), nextTicket(), kiosk, T0);
        entry.pullEvents();
        return entry;
    }

    private QueueEntry calledEntry() {
        var entry = newEntry();
        entry.call(queue, DESK, operator, T0.plusSeconds(60));
        entry.pullEvents();
        return entry;
    }

    private QueueEntry entryInService() {
        var entry = calledEntry();
        entry.startService(operator, T0.plusSeconds(90));
        entry.pullEvents();
        return entry;
    }

    @Nested
    class Creation {

        @Test
        void createsWaitingEntryWithAllData() {
            var key = new IdempotencyKey("req-1");
            var reference = new ExternalReference("PED-123");
            var entry = QueueEntry.create(queue, new NewEntry(SERVICE, "preferencial", reference, key),
                    nextTicket(), kiosk, T0);

            assertThat(entry.id()).isNotNull();
            assertThat(entry.status()).isEqualTo(EntryStatus.WAITING);
            assertThat(entry.createdAt()).isEqualTo(T0);
            assertThat(entry.enqueuedAt()).isEqualTo(T0);
            assertThat(entry.ticket().display()).isEqualTo("A001");
            assertThat(entry.priority()).isEqualTo(new Priority("PREFERENCIAL", 10));
            assertThat(entry.serviceOfferingId()).contains(SERVICE);
            assertThat(entry.externalReference()).contains(reference);
            assertThat(entry.idempotencyKey()).contains(key);
            assertThat(entry.organizationId()).isEqualTo(queue.organizationId());
            assertThat(entry.history()).singleElement()
                    .extracting(EntryHistoryRecord::action).isEqualTo(EntryAction.CREATED);
            assertThat(entry.pullEvents()).singleElement().isInstanceOf(EntryCreated.class);
        }

        @Test
        void rejectsInactiveQueue() {
            queue.pause(T0);

            assertThatThrownBy(() -> QueueEntry.create(queue, new NewEntry(SERVICE, null, null, null),
                    nextTicket(), kiosk, T0)).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void rejectsIncompatibleService() {
            assertThatThrownBy(() -> QueueEntry.create(queue, new NewEntry(ServiceOfferingId.newId(), null, null, null),
                    nextTicket(), kiosk, T0)).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void rejectsTicketFromAnotherQueue() {
            var foreign = new TicketNumber("B", 1, LocalDate.of(2026, 10, 18));

            assertThatThrownBy(() -> QueueEntry.create(queue, new NewEntry(SERVICE, null, null, null),
                    foreign, kiosk, T0)).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void clientCannotSelfAssignRestrictedPriority() {
            assertThatThrownBy(() -> QueueEntry.create(queue, new NewEntry(SERVICE, "URGENTE", null, null),
                    nextTicket(), kiosk, T0)).isInstanceOf(NotAuthorizedException.class);
        }
    }

    @Nested
    class Calling {

        @Test
        void callReservesEntryForOperatorWithDeadline() {
            var entry = newEntry();

            entry.call(queue, DESK, operator, T0.plusSeconds(60));

            assertThat(entry.status()).isEqualTo(EntryStatus.CALLED);
            var call = entry.currentCall().orElseThrow();
            assertThat(call.operatorId()).isEqualTo(operator.operatorId());
            assertThat(call.servicePointId()).isEqualTo(DESK);
            assertThat(call.expiresAt()).isEqualTo(T0.plusSeconds(60).plus(Duration.ofMinutes(3)));
            assertThat(entry.pullEvents()).singleElement().isInstanceOf(EntryCalled.class);
        }

        @Test
        void callRequiresCallPermission() {
            var entry = newEntry();

            assertThatThrownBy(() -> entry.call(queue, DESK, supervisor, T0)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> entry.call(queue, DESK, kiosk, T0)).isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        void cannotCallTwice() {
            var entry = calledEntry();

            assertThatThrownBy(() -> entry.call(queue, DESK, otherOperator, T0.plusSeconds(70)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void cannotCallFromClosedQueue() {
            var entry = newEntry();
            queue.close(T0);

            assertThatThrownBy(() -> entry.call(queue, DESK, operator, T0))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void rejectsQueueOtherThanTheEntryQueue() {
            var entry = newEntry();
            var other = queueWith(QueuePolicy.defaults(OrderingStrategy.FIFO));

            assertThatThrownBy(() -> entry.call(other, DESK, operator, T0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class MissedCalls {

        @Test
        void notPresentReturnsEntryToWaitingKeepingOriginalPosition() {
            var entry = calledEntry();

            entry.registerNotPresent(queue, operator, T0.plusSeconds(200));

            assertThat(entry.status()).isEqualTo(EntryStatus.WAITING);
            assertThat(entry.missedCalls()).isEqualTo(1);
            assertThat(entry.currentCall()).isEmpty();
            assertThat(entry.enqueuedAt()).isEqualTo(T0);
            assertThat(entry.pullEvents()).hasExactlyElementsOfTypes(EntryCallMissed.class, EntryReturnedToQueue.class);
        }

        @Test
        void endOfQueuePolicyResetsArrival() {
            queue.changePolicy(new QueuePolicy(OrderingStrategy.FIFO, Duration.ofMinutes(2), 3,
                    ReturnPosition.END_OF_QUEUE, true), T0);
            var entry = calledEntry();

            entry.registerNotPresent(queue, operator, T0.plusSeconds(200));

            assertThat(entry.enqueuedAt()).isEqualTo(T0.plusSeconds(200));
        }

        @Test
        void onlyCallingOperatorCanRegisterNotPresent() {
            var entry = calledEntry();

            assertThatThrownBy(() -> entry.registerNotPresent(queue, otherOperator, T0.plusSeconds(200)))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        void callExpiresOnlyAfterDeadline() {
            var entry = calledEntry();
            var deadline = entry.currentCall().orElseThrow().expiresAt();

            assertThatThrownBy(() -> entry.expireCall(queue, deadline.minusSeconds(1)))
                    .isInstanceOf(BusinessRuleViolationException.class);

            entry.expireCall(queue, deadline);

            assertThat(entry.status()).isEqualTo(EntryStatus.WAITING);
            assertThat(entry.history().getLast().action()).isEqualTo(EntryAction.CALL_EXPIRED);
            assertThat(entry.history().getLast().actor()).startsWith("system:");
        }

        @Test
        void becomesNoShowAfterMaxCallAttempts() {
            var entry = newEntry();
            for (int attempt = 1; attempt <= 3; attempt++) {
                entry.call(queue, DESK, operator, T0.plusSeconds(attempt * 100L));
                entry.registerNotPresent(queue, operator, T0.plusSeconds(attempt * 100L + 50));
            }

            assertThat(entry.status()).isEqualTo(EntryStatus.NO_SHOW);
            assertThat(entry.status().isTerminal()).isTrue();
            assertThat(entry.pullEvents()).last().isInstanceOf(EntryMarkedNoShow.class);
            assertThatThrownBy(() -> entry.call(queue, DESK, operator, T0.plusSeconds(1000)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void cannotRegisterNotPresentForWaitingEntry() {
            var entry = newEntry();

            assertThatThrownBy(() -> entry.registerNotPresent(queue, operator, T0))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Service {

        @Test
        void startServiceOpensSessionWithCallingOperator() {
            var entry = calledEntry();

            entry.startService(operator, T0.plusSeconds(90));

            assertThat(entry.status()).isEqualTo(EntryStatus.IN_SERVICE);
            assertThat(entry.currentCall()).isEmpty();
            var session = entry.openSession().orElseThrow();
            assertThat(session.operatorId()).isEqualTo(operator.operatorId());
            assertThat(session.servicePointId()).isEqualTo(DESK);
            assertThat(session.startedAt()).isEqualTo(T0.plusSeconds(90));
            assertThat(entry.pullEvents()).singleElement().isInstanceOf(ServiceStarted.class);
        }

        @Test
        void anotherOperatorCannotStartService() {
            var entry = calledEntry();

            assertThatThrownBy(() -> entry.startService(otherOperator, T0.plusSeconds(90)))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        void cannotStartServiceOfWaitingEntry() {
            var entry = newEntry();

            assertThatThrownBy(() -> entry.startService(operator, T0))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void completeClosesSession() {
            var entry = entryInService();

            entry.complete(operator, T0.plusSeconds(390));

            assertThat(entry.status()).isEqualTo(EntryStatus.COMPLETED);
            assertThat(entry.openSession()).isEmpty();
            var session = entry.sessions().getFirst();
            assertThat(session.outcome()).contains(SessionOutcome.COMPLETED);
            assertThat(session.duration()).contains(Duration.ofMinutes(5));
            List<DomainEvent> events = entry.pullEvents();
            assertThat(events).singleElement().isInstanceOfSatisfying(ServiceCompleted.class,
                    e -> assertThat(e.serviceDuration()).isEqualTo(Duration.ofMinutes(5)));
        }

        @Test
        void completedEntryCannotGoBack() {
            var entry = entryInService();
            entry.complete(operator, T0.plusSeconds(390));

            assertThatThrownBy(() -> entry.cancel(supervisor, "x", T0.plusSeconds(400)))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> entry.returnToQueue(queue, operator, "x", T0.plusSeconds(400)))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> entry.call(queue, DESK, operator, T0.plusSeconds(400)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void cannotCompleteWaitingEntry() {
            var entry = newEntry();

            assertThatThrownBy(() -> entry.complete(operator, T0)).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void returnToQueueClosesSessionAndAllowsNewSession() {
            var entry = entryInService();

            entry.returnToQueue(queue, operator, "aguardar exame", T0.plusSeconds(200));

            assertThat(entry.status()).isEqualTo(EntryStatus.WAITING);
            assertThat(entry.sessions().getFirst().outcome()).contains(SessionOutcome.RETURNED_TO_QUEUE);
            assertThat(entry.pullEvents()).singleElement().isInstanceOf(EntryReturnedToQueue.class);

            entry.call(queue, DESK, otherOperator, T0.plusSeconds(300));
            entry.startService(otherOperator, T0.plusSeconds(320));
            entry.complete(otherOperator, T0.plusSeconds(400));

            assertThat(entry.sessions()).hasSize(2)
                    .extracting(ServiceSession::operatorId)
                    .containsExactly(operator.operatorId(), otherOperator.operatorId());
        }

        @Test
        void returnToQueueRespectsPolicy() {
            queue.changePolicy(new QueuePolicy(OrderingStrategy.FIFO, Duration.ofMinutes(2), 3,
                    ReturnPosition.KEEP_ORIGINAL, false), T0);
            var entry = entryInService();

            assertThatThrownBy(() -> entry.returnToQueue(queue, operator, null, T0.plusSeconds(200)))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void selfServiceMayGiveUpWhileWaiting() {
            var entry = newEntry();

            entry.cancel(kiosk, "desistiu", T0.plusSeconds(10));

            assertThat(entry.status()).isEqualTo(EntryStatus.CANCELLED);
            assertThat(entry.pullEvents()).singleElement().isInstanceOfSatisfying(EntryCancelled.class,
                    e -> assertThat(e.previousStatus()).isEqualTo(EntryStatus.WAITING));
        }

        @Test
        void selfServiceCannotCancelCalledEntry() {
            var entry = calledEntry();

            assertThatThrownBy(() -> entry.cancel(kiosk, null, T0.plusSeconds(70)))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        void operatorWithoutPermissionCannotCancel() {
            var entry = newEntry();

            assertThatThrownBy(() -> entry.cancel(operator, null, T0)).isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        void cancellingDuringServiceClosesSession() {
            var entry = entryInService();

            entry.cancel(supervisor, "atendimento interrompido", T0.plusSeconds(200));

            assertThat(entry.status()).isEqualTo(EntryStatus.CANCELLED);
            assertThat(entry.sessions().getFirst().outcome()).contains(SessionOutcome.CANCELLED);
        }

        @Test
        void cancelledEntryIsTerminal() {
            var entry = newEntry();
            entry.cancel(supervisor, null, T0);

            assertThatThrownBy(() -> entry.call(queue, DESK, operator, T0))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> entry.cancel(supervisor, null, T0))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    class PriorityChanges {

        @Test
        void authorizedStaffCanReevaluatePriorityWhileWaiting() {
            var entry = newEntry();

            entry.changePriority(queue, "URGENTE", supervisor, T0.plusSeconds(30));

            assertThat(entry.priority()).isEqualTo(new Priority("URGENTE", 50));
            assertThat(entry.waitingPosition().priorityLevel()).isEqualTo(50);
            assertThat(entry.history().getLast().action()).isEqualTo(EntryAction.PRIORITY_CHANGED);
            assertThat(entry.pullEvents()).singleElement().isInstanceOf(EntryPriorityChanged.class);
        }

        @Test
        void requiresChangePriorityPermission() {
            var entry = newEntry();

            assertThatThrownBy(() -> entry.changePriority(queue, "PREFERENCIAL", operator, T0))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        void restrictedTargetAlsoRequiresRestrictedPermission() {
            var entry = newEntry();
            var changer = new Actor.Staff(OperatorId.newId(), Set.of(Permission.CHANGE_PRIORITY));

            assertThatThrownBy(() -> entry.changePriority(queue, "URGENTE", changer, T0))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        void cannotChangePriorityAfterCall() {
            var entry = calledEntry();

            assertThatThrownBy(() -> entry.changePriority(queue, "PREFERENCIAL", supervisor, T0))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Restoration {

        @Test
        void rejectsInconsistentState() {
            var entry = newEntry();
            var snapshot = new QueueEntry.Snapshot(entry.id(), entry.organizationId(), entry.queueId(), null,
                    entry.ticket(), null, null, Priority.NORMAL, EntryStatus.CALLED, T0, T0, null, 0,
                    List.of(), List.of(), 1);

            assertThatThrownBy(() -> QueueEntry.restore(snapshot)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void historyTracksEveryTransition() {
            var entry = entryInService();
            entry.complete(operator, T0.plusSeconds(400));

            assertThat(entry.history()).extracting(EntryHistoryRecord::to).containsExactly(
                    EntryStatus.WAITING, EntryStatus.CALLED, EntryStatus.IN_SERVICE, EntryStatus.COMPLETED);
        }
    }
}
