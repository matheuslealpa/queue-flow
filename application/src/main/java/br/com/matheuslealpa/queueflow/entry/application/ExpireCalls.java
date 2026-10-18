package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;

import java.time.Clock;

/**
 * Processa um lote de chamadas cujo prazo de comparecimento terminou. Pensado para execução
 * periódica; cada entrada é tratada em sua própria transação, de modo que uma falha (p.ex.
 * conflito otimista com um início de atendimento simultâneo) não afeta as demais.
 */
public final class ExpireCalls {

    private static final System.Logger LOG = System.getLogger(ExpireCalls.class.getName());

    private final UnitOfWork unitOfWork;
    private final QueueEntryRepository entries;
    private final EntryModifier modifier;
    private final Clock clock;

    public ExpireCalls(UnitOfWork unitOfWork, QueueEntryRepository entries, QueueRepository queues,
                       DomainEventPublisher events, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.entries = entries;
        this.modifier = new EntryModifier(unitOfWork, entries, queues, events);
        this.clock = clock;
    }

    /** @return quantidade de chamadas expiradas neste lote */
    public int handle(int batchSize) {
        var now = clock.instant();
        var candidates = unitOfWork.execute(() -> entries.findExpiredCalls(now, batchSize));
        var expired = 0;
        for (var candidate : candidates) {
            try {
                var result = modifier.modify(candidate.organizationId(), candidate.id(), (entry, queue) -> {
                    if (entry.status() == EntryStatus.CALLED && entry.currentCall().orElseThrow().isExpiredAt(now)) {
                        entry.expireCall(queue, now);
                    }
                });
                if (result.status() != EntryStatus.CALLED) {
                    expired++;
                }
            } catch (RuntimeException e) {
                // Outra transação alterou a entrada (p.ex. atendimento iniciado); o próximo ciclo reavalia.
                LOG.log(System.Logger.Level.WARNING, "Falha ao expirar chamada da entrada " + candidate.id(), e);
            }
        }
        return expired;
    }
}
