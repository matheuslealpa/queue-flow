package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.domain.DomainEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(List<DomainEvent> events) {
        published.addAll(events);
    }

    public List<DomainEvent> published() {
        return List.copyOf(published);
    }

    public <E extends DomainEvent> List<E> published(Class<E> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
