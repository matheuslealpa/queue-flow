package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.queue.domain.EntrySelection;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityBand;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.List;

/**
 * Entradas em espera na ordem em que serão chamadas, conforme a política vigente da fila.
 * Com intercalação, a ordem é projetada pela fila a partir das entradas com e sem prioridade.
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
        if (!queue.policy().interleavesPriorities()) {
            return entries.findWaiting(organizationId, queueId, EntrySelection.any(queue.ordering()), offset, limit)
                    .stream().map(EntryView::of).toList();
        }
        // Cada faixa contribui com no máximo offset + limit posições da projeção.
        var window = offset + limit;
        var prioritized = entries.findWaiting(organizationId, queueId,
                new EntrySelection(queue.ordering(), PriorityBand.PRIORITIZED), 0, window);
        var regular = entries.findWaiting(organizationId, queueId,
                new EntrySelection(queue.ordering(), PriorityBand.REGULAR), 0, window);
        var projected = queue.projectCallOrder(prioritized, regular);
        return projected.subList(Math.min(offset, projected.size()), Math.min(window, projected.size())).stream()
                .map(EntryView::of)
                .toList();
    }
}
