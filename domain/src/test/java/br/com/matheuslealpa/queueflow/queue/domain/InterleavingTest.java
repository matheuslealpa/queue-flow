package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterleavingTest {

    private static final Priority PREFERENTIAL = new Priority("PREFERENCIAL", 10);

    private Queue queue(int maxConsecutivePriorityCalls) {
        return Queue.create(OrganizationId.newId(), LocationId.newId(), "Fila", "A",
                QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO).withInterleaving(maxConsecutivePriorityCalls),
                Set.of(), List.of(new PriorityClassification("PREFERENCIAL", "Preferencial", 10, false)),
                Instant.parse("2026-10-18T12:00:00Z"));
    }

    @Test
    void interleavingRequiresPriorityFifoAndBoundedLimit() {
        assertThatThrownBy(() -> QueuePolicy.defaults(OrderingStrategy.FIFO).withInterleaving(2))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO).withInterleaving(-1))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO).withInterleaving(11))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(QueuePolicy.defaults(OrderingStrategy.PRIORITY_FIFO).interleavesPriorities()).isFalse();
    }

    @Test
    void strictPriorityNeverRestrictsSelection() {
        var queue = queue(0);
        for (int i = 0; i < 5; i++) {
            assertThat(queue.registerCall(PREFERENTIAL)).isFalse();
        }
        assertThat(queue.callSelectionPlan()).extracting(EntrySelection::band).containsExactly(PriorityBand.ANY);
    }

    @Test
    void afterLimitPrefersRegularThenFallsBackToAny() {
        var queue = queue(2);

        queue.registerCall(PREFERENTIAL);
        assertThat(queue.callSelectionPlan()).extracting(EntrySelection::band).containsExactly(PriorityBand.ANY);

        queue.registerCall(PREFERENTIAL);
        assertThat(queue.consecutivePriorityCalls()).isEqualTo(2);
        assertThat(queue.callSelectionPlan()).extracting(EntrySelection::band)
                .containsExactly(PriorityBand.REGULAR, PriorityBand.ANY);

        queue.registerCall(Priority.NORMAL);
        assertThat(queue.consecutivePriorityCalls()).isZero();
        assertThat(queue.callSelectionPlan()).extracting(EntrySelection::band).containsExactly(PriorityBand.ANY);
    }

    @Test
    void projectsTwoToOneOrder() {
        var queue = queue(2);

        var order = queue.projectCallOrder(List.of("P1", "P2", "P3", "P4", "P5"), List.of("N1", "N2"));

        assertThat(order).containsExactly("P1", "P2", "N1", "P3", "P4", "N2", "P5");
    }

    @Test
    void projectionStartsFromCurrentStreak() {
        var queue = queue(2);
        queue.registerCall(PREFERENTIAL);

        assertThat(queue.projectCallOrder(List.of("P1", "P2"), List.of("N1")))
                .containsExactly("P1", "N1", "P2");
    }

    @Test
    void projectionWithoutInterleavingIsStrictPriority() {
        assertThat(queue(0).projectCallOrder(List.of("P1", "P2"), List.of("N1", "N2")))
                .containsExactly("P1", "P2", "N1", "N2");
    }

    @Test
    void regularOnlyQueueOrOnlyPrioritizedAreServedWithoutGaps() {
        var queue = queue(1);

        assertThat(queue.projectCallOrder(List.of(), List.of("N1", "N2"))).containsExactly("N1", "N2");
        assertThat(queue.projectCallOrder(List.of("P1", "P2"), List.of())).containsExactly("P1", "P2");
    }
}
