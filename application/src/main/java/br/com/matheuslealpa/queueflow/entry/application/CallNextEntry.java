package br.com.matheuslealpa.queueflow.entry.application;

import br.com.matheuslealpa.queueflow.entry.domain.QueueEntry;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Clock;
import java.util.Optional;

/**
 * Chama a próxima entrada da fila para um operador. A seleção e a reserva ocorrem na mesma
 * transação, com bloqueio da linha selecionada; operadores concorrentes recebem entradas distintas.
 *
 * <p>Com intercalação de prioridades, a fila também é bloqueada: o contador de chamadas
 * prioritárias seguidas precisa ser lido e atualizado sem que outra chamada intervenha.
 */
public final class CallNextEntry {

    public record Command(OrganizationId organizationId, QueueId queueId, OperatorId operatorId,
                          ServicePointId servicePointId) {
    }

    private final UnitOfWork unitOfWork;
    private final QueueRepository queues;
    private final QueueEntryRepository entries;
    private final ServicePointRepository servicePoints;
    private final ActorResolver actors;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CallNextEntry(UnitOfWork unitOfWork, QueueRepository queues, QueueEntryRepository entries,
                         ServicePointRepository servicePoints, ActorResolver actors, DomainEventPublisher events,
                         Clock clock) {
        this.unitOfWork = unitOfWork;
        this.queues = queues;
        this.entries = entries;
        this.servicePoints = servicePoints;
        this.actors = actors;
        this.events = events;
        this.clock = clock;
    }

    /** @return a entrada chamada, ou vazio se não houver ninguém aguardando */
    public Optional<EntryView> handle(Command command) {
        return unitOfWork.execute(() -> {
            var org = command.organizationId();
            var queue = queues.findById(org, command.queueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fila", command.queueId()));
            if (queue.policy().interleavesPriorities()) {
                queue = queues.lockById(org, queue.id()).orElseThrow();
            }
            var servicePoint = servicePoints.findById(org, command.servicePointId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ponto de atendimento", command.servicePointId()));
            var operator = actors.operator(org, command.operatorId());
            queue.ensureAllowsCalls();
            operator.ensureCanServe(queue, servicePoint);

            Optional<QueueEntry> next = Optional.empty();
            for (var selection : queue.callSelectionPlan()) {
                next = entries.lockNextWaiting(org, queue.id(), selection);
                if (next.isPresent()) {
                    break;
                }
            }
            if (next.isEmpty()) {
                return Optional.<EntryView>empty();
            }
            var entry = next.get();
            entry.call(queue, servicePoint.id(), operator.toActor(), clock.instant());
            entries.save(entry);
            if (queue.registerCall(entry.priority())) {
                queues.save(queue);
            }
            events.publish(entry.pullEvents());
            return Optional.of(EntryView.of(entry));
        });
    }
}
