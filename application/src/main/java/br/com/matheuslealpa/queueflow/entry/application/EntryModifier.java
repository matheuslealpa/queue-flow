package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.QueueEntry;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

/**
 * Fluxo comum às operações sobre uma entrada existente: carregar entrada e fila na mesma
 * transação, aplicar a operação de domínio, persistir e publicar os eventos resultantes.
 */
final class EntryModifier {

    @FunctionalInterface
    interface Operation {
        void apply(QueueEntry entry, Queue queue);
    }

    private final UnitOfWork unitOfWork;
    private final QueueEntryRepository entries;
    private final QueueRepository queues;
    private final DomainEventPublisher events;

    EntryModifier(UnitOfWork unitOfWork, QueueEntryRepository entries, QueueRepository queues,
                  DomainEventPublisher events) {
        this.unitOfWork = unitOfWork;
        this.entries = entries;
        this.queues = queues;
        this.events = events;
    }

    EntryView modify(OrganizationId organizationId, QueueEntryId entryId, Operation operation) {
        return unitOfWork.execute(() -> {
            var entry = entries.findById(organizationId, entryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Entrada", entryId));
            var queue = queues.findById(organizationId, entry.queueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fila", entry.queueId()));
            operation.apply(entry, queue);
            entries.save(entry);
            events.publish(entry.pullEvents());
            return EntryView.of(entry);
        });
    }
}
