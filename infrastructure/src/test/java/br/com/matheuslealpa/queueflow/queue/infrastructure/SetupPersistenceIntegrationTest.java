package br.com.matheuslealpa.queueflow.queue.infrastructure;

import br.com.matheuslealpa.queueflow.operator.domain.OperatorAssignment;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatus;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;
import br.com.matheuslealpa.queueflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SetupPersistenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired QueueRepository queues;
    @Autowired OperatorRepository operators;

    @Test
    void queueRoundTripKeepsPolicyClassificationsAndServices() {
        var tenant = newTenant();
        var id = newQueue(tenant, OrderingStrategy.PRIORITY_FIFO);

        var queue = queues.findById(tenant.org(), id).orElseThrow();
        assertThat(queue.policy().ordering()).isEqualTo(OrderingStrategy.PRIORITY_FIFO);
        assertThat(queue.policy().callTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(queue.classifications()).extracting(PriorityClassification::code)
                .containsExactly("NORMAL", "PREFERENCIAL");
        assertThat(queue.acceptedServices()).containsExactly(tenant.service());

        queue.pause(clock.instant());
        queue.defineClassification(new PriorityClassification("URGENTE", "Urgente", 50, true));
        queues.save(queue);

        var reloaded = queues.findById(tenant.org(), id).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(QueueStatus.PAUSED);
        assertThat(reloaded.classification("URGENTE")).map(PriorityClassification::restricted).contains(true);
        assertThat(reloaded.version()).isEqualTo(1);
        assertThat(queues.findById(newTenant().org(), id)).isEmpty();

        assertThatThrownBy(() -> queues.save(queue)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void operatorRoundTripKeepsPermissionsAndAssignments() {
        var tenant = newTenant();
        var queue = newQueue(tenant, OrderingStrategy.FIFO);
        var desk = newServicePoint(tenant, "Guichê 1");
        var operatorId = newAttendant(tenant, queue);

        var operator = operators.findById(tenant.org(), operatorId).orElseThrow();
        operator.grant(Permission.CANCEL_ENTRY);
        operator.assign(new OperatorAssignment(queue, desk));
        operators.save(operator);

        var reloaded = operators.findById(tenant.org(), operatorId).orElseThrow();
        assertThat(reloaded.permissions()).containsExactlyInAnyOrder(Permission.CALL_ENTRIES, Permission.CANCEL_ENTRY);
        assertThat(reloaded.assignments()).containsExactlyInAnyOrder(
                OperatorAssignment.anyServicePoint(queue), new OperatorAssignment(queue, desk));
    }
}
