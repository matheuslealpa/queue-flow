package br.com.matheuslealpa.queueflow.queue.application;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.QueueCreated;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatus;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatusChanged;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.testing.QueueFlowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueManagementTest {

    private QueueFlowFixture fx;

    @BeforeEach
    void setUp() {
        fx = new QueueFlowFixture();
    }

    private CreateQueue.Command command(LocationId locationId, Set<ServiceOfferingId> services) {
        return new CreateQueue.Command(fx.org, fx.manager.id(), locationId, "Laboratório", "L",
                QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO), services,
                List.of(new PriorityClassification("PREFERENCIAL", "Preferencial", 10, false)));
    }

    @Test
    void createsQueue() {
        var view = fx.createQueue.handle(command(fx.location.id(), Set.of(fx.service.id())));

        assertThat(view.status()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(view.acceptedServices()).containsExactly(fx.service.id().value());
        assertThat(view.classifications()).extracting(QueueView.Classification::code)
                .containsExactly("NORMAL", "PREFERENCIAL");
        assertThat(fx.getQueue.handle(fx.org, new QueueId(view.id()))).isEqualTo(view);
        assertThat(fx.events.published(QueueCreated.class)).hasSize(1);
    }

    @Test
    void requiresManageQueuesPermission() {
        var queue = fx.queue(OrderingStrategy.FIFO);
        var attendant = fx.attendant(queue);

        assertThatThrownBy(() -> fx.createQueue.handle(new CreateQueue.Command(fx.org, attendant.id(),
                fx.location.id(), "Fila", "F", QueuePolicy.defaults(OrderingStrategy.FIFO), Set.of(), List.of())))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void rejectsLocationOrServiceFromAnotherOrganization() {
        assertThatThrownBy(() -> fx.createQueue.handle(command(LocationId.newId(), Set.of())))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> fx.createQueue.handle(command(fx.location.id(), Set.of(ServiceOfferingId.newId()))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changesStatus() {
        var queue = fx.queue(OrderingStrategy.FIFO);

        var paused = fx.changeQueueStatus.handle(new ChangeQueueStatus.Command(fx.org, fx.manager.id(), queue.id(),
                QueueStatus.PAUSED));

        assertThat(paused.status()).isEqualTo(QueueStatus.PAUSED);
        assertThat(fx.events.published(QueueStatusChanged.class)).hasSize(1);
        assertThatThrownBy(() -> fx.getQueue.handle(OrganizationId.newId(), queue.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
