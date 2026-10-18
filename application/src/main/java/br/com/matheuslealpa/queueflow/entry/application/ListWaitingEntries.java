package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.List;

/**
 * Entradas em espera na ordem em que serão chamadas, conforme a política vigente da fila.
 */
public final class ListWaitingEntries {

    public static final int MAX_PAGE_SIZE = 200;

    private final QueueRepository queues;
    private final QueueEntryRepository entries;

    public ListWaitingEntries(QueueRepository queues, QueueEntryRepository entries) {
        this.queues = queues;
        this.entries = entries;
    }

    public List<EntryView> handle(OrganizationId organizationId, QueueId queueId, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new BusinessRuleViolationException("Paginação inválida: offset >= 0 e 1 <= limit <= " + MAX_PAGE_SIZE);
        }
        var queue = queues.findById(organizationId, queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Fila", queueId));
        return entries.findWaiting(organizationId, queueId, queue.ordering(), offset, limit).stream()
                .map(EntryView::of)
                .toList();
    }
}
