package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

public final class GetEntry {

    private final QueueEntryRepository entries;

    public GetEntry(QueueEntryRepository entries) {
        this.entries = entries;
    }

    public EntryView handle(OrganizationId organizationId, QueueEntryId entryId) {
        return entries.findById(organizationId, entryId)
                .map(EntryView::of)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada", entryId));
    }
}
