package br.com.matheuslealpa.queueflow.queue.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryOrderingTest {

    private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");

    private final WaitingPosition normalFirst = new WaitingPosition(0, T0, 1);
    private final WaitingPosition priorityLater = new WaitingPosition(10, T0.plusSeconds(60), 2);
    private final WaitingPosition normalLast = new WaitingPosition(0, T0.plusSeconds(120), 3);
    private final WaitingPosition priorityLast = new WaitingPosition(10, T0.plusSeconds(180), 4);

    private List<WaitingPosition> sorted(OrderingStrategy strategy) {
        return List.of(priorityLast, normalLast, priorityLater, normalFirst).stream()
                .sorted(strategy.ordering().comparator())
                .toList();
    }

    @Test
    void fifoOrdersByArrivalIgnoringPriority() {
        assertThat(sorted(OrderingStrategy.FIFO))
                .containsExactly(normalFirst, priorityLater, normalLast, priorityLast);
    }

    @Test
    void priorityFifoOrdersByLevelThenArrival() {
        assertThat(sorted(OrderingStrategy.PRIORITY_FIFO))
                .containsExactly(priorityLater, priorityLast, normalFirst, normalLast);
    }

    @Test
    void ticketSequenceBreaksTiesOfSameInstant() {
        var second = new WaitingPosition(0, T0, 8);
        var first = new WaitingPosition(0, T0, 7);

        assertThat(List.of(second, first).stream().sorted(OrderingStrategy.PRIORITY_FIFO.ordering().comparator()))
                .containsExactly(first, second);
    }

    @Test
    void exposesDeclarativeKeysForPersistenceAdapters() {
        assertThat(OrderingStrategy.PRIORITY_FIFO.ordering().keys())
                .containsExactly(SortKey.PRIORITY_LEVEL_DESC, SortKey.ENQUEUED_AT_ASC, SortKey.TICKET_SEQUENCE_ASC);
    }
}
