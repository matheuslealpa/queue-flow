package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.shared.domain.Actor;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.InvalidStateTransitionException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final ServiceOfferingId PHARMACY = ServiceOfferingId.newId();
    private static final PriorityClassification PREFERENTIAL =
            new PriorityClassification("preferencial", "Preferencial", 10, false);
    private static final PriorityClassification URGENT =
            new PriorityClassification("URGENTE", "Urgente", 50, true);

    private final Actor kiosk = new Actor.SelfService("kiosk-1");
    private final Actor supervisor = new Actor.Staff(OperatorId.newId(), Set.of(Permission.ASSIGN_RESTRICTED_PRIORITY));

    private Queue queue(Set<ServiceOfferingId> services) {
        return Queue.create(OrganizationId.newId(), LocationId.newId(), "Farmácia", "a",
                QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO), services, List.of(PREFERENTIAL, URGENT), NOW);
    }

    @Test
    void createsActiveQueueWithNormalClassificationAndEvent() {
        var queue = queue(Set.of());

        assertThat(queue.status()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(queue.ticketPrefix()).isEqualTo("A");
        assertThat(queue.classifications()).extracting(PriorityClassification::code)
                .containsExactly("NORMAL", "PREFERENCIAL", "URGENTE");
        assertThat(queue.pullEvents()).singleElement().isInstanceOf(QueueCreated.class);
    }

    @Test
    void rejectsInvalidTicketPrefix() {
        assertThatThrownBy(() -> Queue.create(OrganizationId.newId(), LocationId.newId(), "Fila", "ABC-1",
                QueuePolicy.defaults(OrderingStrategy.FIFO), Set.of(), List.of(), NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void acceptsOnlyConfiguredServices() {
        var queue = queue(Set.of(PHARMACY));

        assertThatCode(() -> queue.ensureAcceptsEntry(PHARMACY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> queue.ensureAcceptsEntry(ServiceOfferingId.newId()))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> queue.ensureAcceptsEntry(null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void queueWithoutServicesRejectsEntryRequestingService() {
        var queue = queue(Set.of());

        assertThatCode(() -> queue.ensureAcceptsEntry(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> queue.ensureAcceptsEntry(PHARMACY))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void pausedQueueRejectsEntriesButAllowsCalls() {
        var queue = queue(Set.of());
        queue.pause(NOW);

        assertThatThrownBy(() -> queue.ensureAcceptsEntry(null)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatCode(queue::ensureAllowsCalls).doesNotThrowAnyException();
    }

    @Test
    void closedQueueIsTerminal() {
        var queue = queue(Set.of());
        queue.close(NOW);

        assertThatThrownBy(queue::ensureAllowsCalls).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> queue.activate(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> queue.acceptService(PHARMACY)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void statusChangesEmitEvents() {
        var queue = queue(Set.of());
        queue.pullEvents();

        queue.pause(NOW);
        queue.pause(NOW);
        queue.activate(NOW);

        assertThat(queue.pullEvents()).extracting(e -> ((QueueStatusChanged) e).to())
                .containsExactly(QueueStatus.PAUSED, QueueStatus.ACTIVE);
    }

    @Test
    void resolvesPriorityFromClassificationCode() {
        var queue = queue(Set.of());

        assertThat(queue.resolvePriority(null, kiosk)).isEqualTo(Priority.NORMAL);
        assertThat(queue.resolvePriority("preferencial", kiosk)).isEqualTo(new Priority("PREFERENCIAL", 10));
    }

    @Test
    void rejectsUnknownClassification() {
        var queue = queue(Set.of());

        assertThatThrownBy(() -> queue.resolvePriority("VIP", kiosk))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void restrictedClassificationRequiresPermission() {
        var queue = queue(Set.of());

        assertThatThrownBy(() -> queue.resolvePriority("URGENTE", kiosk)).isInstanceOf(NotAuthorizedException.class);
        assertThat(queue.resolvePriority("URGENTE", supervisor).level()).isEqualTo(50);
    }

    @Test
    void normalClassificationCannotBeRemovedOrElevated() {
        var queue = queue(Set.of());

        assertThatThrownBy(() -> queue.removeClassification("normal"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> new PriorityClassification("NORMAL", "Normal", 5, false))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
