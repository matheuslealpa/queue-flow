package br.com.matheuslealpa.queueflow.entry.infrastructure;

import br.com.matheuslealpa.queueflow.entry.application.CompleteService;
import br.com.matheuslealpa.queueflow.entry.application.EnqueueEntry;
import br.com.matheuslealpa.queueflow.entry.application.ExpireCalls;
import br.com.matheuslealpa.queueflow.entry.application.ReturnEntryToQueue;
import br.com.matheuslealpa.queueflow.entry.application.StartService;
import br.com.matheuslealpa.queueflow.entry.domain.EntryAction;
import br.com.matheuslealpa.queueflow.entry.domain.EntryHistoryRecord;
import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.entry.domain.ExternalReference;
import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.entry.domain.ServiceSession;
import br.com.matheuslealpa.queueflow.entry.domain.SessionOutcome;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.Priority;
import br.com.matheuslealpa.queueflow.reporting.application.GetQueueMetrics;
import br.com.matheuslealpa.queueflow.shared.domain.Actor;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryPersistenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired QueueEntryRepository entries;
    @Autowired StartService startService;
    @Autowired CompleteService completeService;
    @Autowired ReturnEntryToQueue returnEntryToQueue;
    @Autowired ExpireCalls expireCalls;
    @Autowired GetQueueMetrics getQueueMetrics;

    @Test
    void persistsAndRestoresFullLifecycle() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.PRIORITY_FIFO);
        var operator = newAttendant(tenant, queue);
        var desk = newServicePoint(tenant, "Guichê 1");
        var created = enqueueEntry.handle(new EnqueueEntry.Command(tenant.org(), queue, tenant.service(),
                "preferencial", new ExternalReference("PED-9"), new IdempotencyKey("req-9"), KIOSK));
        var id = new QueueEntryId(created.id());

        callNext(tenant, queue, operator, desk);
        clock.advance(Duration.ofSeconds(30));
        startService.handle(new StartService.Command(tenant.org(), id, operator));
        clock.advance(Duration.ofMinutes(2));
        returnEntryToQueue.handle(new ReturnEntryToQueue.Command(tenant.org(), id, operator, "aguardar exame"));
        callNext(tenant, queue, operator, desk);
        startService.handle(new StartService.Command(tenant.org(), id, operator));
        clock.advance(Duration.ofMinutes(3));
        completeService.handle(new CompleteService.Command(tenant.org(), id, operator));

        var entry = entries.findById(tenant.org(), id).orElseThrow();
        assertThat(entry.status()).isEqualTo(EntryStatus.COMPLETED);
        assertThat(entry.priority()).isEqualTo(new Priority("PREFERENCIAL", 10));
        assertThat(entry.ticket().display()).isEqualTo("A001");
        assertThat(entry.ticket().operationalDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(entry.externalReference()).contains(new ExternalReference("PED-9"));
        assertThat(entry.idempotencyKey()).contains(new IdempotencyKey("req-9"));
        assertThat(entry.currentCall()).isEmpty();
        assertThat(entry.sessions()).extracting(s -> s.outcome().orElseThrow())
                .containsExactly(SessionOutcome.RETURNED_TO_QUEUE, SessionOutcome.COMPLETED);
        assertThat(entry.sessions()).extracting(ServiceSession::duration)
                .containsExactly(java.util.Optional.of(Duration.ofMinutes(2)), java.util.Optional.of(Duration.ofMinutes(3)));
        assertThat(entry.history()).extracting(EntryHistoryRecord::action).containsExactly(
                EntryAction.CREATED, EntryAction.CALLED, EntryAction.SERVICE_STARTED, EntryAction.RETURNED_TO_QUEUE,
                EntryAction.CALLED, EntryAction.SERVICE_STARTED, EntryAction.COMPLETED);
        assertThat(entry.version()).isEqualTo(6);
    }

    @Test
    void staleCopyIsRejectedByOptimisticLocking() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var id = new QueueEntryId(enqueue(tenant, queue, null).id());
        var copyA = entries.findById(tenant.org(), id).orElseThrow();
        var copyB = entries.findById(tenant.org(), id).orElseThrow();
        var system = new Actor.SystemProcess("test");

        copyA.cancel(system, "A", clock.instant());
        entries.save(copyA);
        copyB.cancel(system, "B", clock.instant());

        assertThatThrownBy(() -> entries.save(copyB)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(entries.findById(tenant.org(), id).orElseThrow().history().getLast().detail()).isEqualTo("A");
    }

    @Test
    void entriesAreIsolatedBetweenOrganizations() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var id = new QueueEntryId(enqueue(tenant, queue, null).id());
        var otherOrg = newTenant().org();

        assertThat(entries.findById(otherOrg, id)).isEmpty();
        assertThat(entries.findById(OrganizationId.newId(), id)).isEmpty();
        assertThat(entries.findWaiting(otherOrg, queue, OrderingStrategy.FIFO.ordering(), 0, 10)).isEmpty();
        assertThat(entries.lockNextWaiting(otherOrg, queue, OrderingStrategy.FIFO.ordering())).isEmpty();
        assertThat(entries.findById(tenant.org(), id)).isPresent();
    }

    @Test
    void expiresOverdueCallsAndReportsMetrics() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var operator = newAttendant(tenant, queue);
        var desk = newServicePoint(tenant, "Guichê 1");
        var overdue = new QueueEntryId(enqueue(tenant, queue, null).id());
        var served = new QueueEntryId(enqueue(tenant, queue, null).id());
        enqueue(tenant, queue, null);

        callNext(tenant, queue, operator, desk);
        clock.advance(Duration.ofMinutes(4)); // prazo padrão: 3 minutos
        expireCalls.handle(500);

        var expired = entries.findById(tenant.org(), overdue).orElseThrow();
        assertThat(expired.status()).isEqualTo(EntryStatus.WAITING);
        assertThat(expired.missedCalls()).isEqualTo(1);
        assertThat(expired.history().getLast().action()).isEqualTo(EntryAction.CALL_EXPIRED);

        callNext(tenant, queue, operator, desk); // a entrada expirada mantém a posição original
        assertThat(entries.findById(tenant.org(), overdue).orElseThrow().status()).isEqualTo(EntryStatus.CALLED);
        assertThat(entries.findById(tenant.org(), served).orElseThrow().status()).isEqualTo(EntryStatus.WAITING);

        startService.handle(new StartService.Command(tenant.org(), overdue, operator));
        clock.advance(Duration.ofMinutes(2));
        completeService.handle(new CompleteService.Command(tenant.org(), overdue, operator));

        var metrics = getQueueMetrics.handle(tenant.org(), queue);
        assertThat(metrics.waiting()).isEqualTo(2);
        assertThat(metrics.completed()).isEqualTo(1);
        assertThat(metrics.averageServiceSeconds()).isEqualTo(120.0);
        assertThat(metrics.averageWaitSeconds()).isEqualTo(240.0);
    }
}
