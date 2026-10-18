package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.entry.application.CallNextEntry;
import br.com.matheuslealpa.queueflow.entry.application.CancelEntry;
import br.com.matheuslealpa.queueflow.entry.application.ChangeEntryPriority;
import br.com.matheuslealpa.queueflow.entry.application.CompleteService;
import br.com.matheuslealpa.queueflow.entry.application.EnqueueEntry;
import br.com.matheuslealpa.queueflow.entry.application.EntryView;
import br.com.matheuslealpa.queueflow.entry.application.ExpireCalls;
import br.com.matheuslealpa.queueflow.entry.application.GetEntry;
import br.com.matheuslealpa.queueflow.entry.application.ListWaitingEntries;
import br.com.matheuslealpa.queueflow.entry.application.RegisterNotPresent;
import br.com.matheuslealpa.queueflow.entry.application.ReturnEntryToQueue;
import br.com.matheuslealpa.queueflow.entry.application.StartService;
import br.com.matheuslealpa.queueflow.location.domain.Location;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorAssignment;
import br.com.matheuslealpa.queueflow.queue.application.ChangeQueueStatus;
import br.com.matheuslealpa.queueflow.queue.application.CreateQueue;
import br.com.matheuslealpa.queueflow.queue.application.GetQueue;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOffering;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.shared.application.Requester;
import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Monta os casos de uso sobre adapters em memória e um cenário base: uma organização com
 * uma unidade, um serviço, um gestor e pontos de atendimento.
 */
public final class QueueFlowFixture {

    public static final Requester KIOSK = new Requester.SelfService("kiosk-1");

    public final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
    public final InMemoryQueueRepository queues = new InMemoryQueueRepository();
    public final InMemoryQueueEntryRepository entries = new InMemoryQueueEntryRepository();
    public final InMemoryLocationRepository locations = new InMemoryLocationRepository();
    public final InMemoryServiceOfferingRepository services = new InMemoryServiceOfferingRepository();
    public final InMemoryServicePointRepository servicePoints = new InMemoryServicePointRepository();
    public final InMemoryOperatorRepository operators = new InMemoryOperatorRepository();
    public final InMemoryUnitOfWork unitOfWork = new InMemoryUnitOfWork(entries);
    public final InMemoryTicketSequence tickets = new InMemoryTicketSequence();
    public final RecordingEventPublisher events = new RecordingEventPublisher();
    public final ActorResolver actors = new ActorResolver(operators);

    public final CreateQueue createQueue = new CreateQueue(unitOfWork, queues, locations, services, actors, events, clock);
    public final ChangeQueueStatus changeQueueStatus = new ChangeQueueStatus(unitOfWork, queues, actors, events, clock);
    public final GetQueue getQueue = new GetQueue(queues);
    public final EnqueueEntry enqueueEntry = new EnqueueEntry(unitOfWork, queues, entries, locations, services,
            tickets, actors, events, clock);
    public final CallNextEntry callNext = new CallNextEntry(unitOfWork, queues, entries, servicePoints, actors,
            events, clock);
    public final StartService startService = new StartService(unitOfWork, entries, queues, actors, events, clock);
    public final CompleteService completeService = new CompleteService(unitOfWork, entries, queues, actors, events, clock);
    public final RegisterNotPresent registerNotPresent = new RegisterNotPresent(unitOfWork, entries, queues, actors,
            events, clock);
    public final ReturnEntryToQueue returnToQueue = new ReturnEntryToQueue(unitOfWork, entries, queues, actors,
            events, clock);
    public final CancelEntry cancelEntry = new CancelEntry(unitOfWork, entries, queues, actors, events, clock);
    public final ChangeEntryPriority changePriority = new ChangeEntryPriority(unitOfWork, entries, queues, actors,
            events, clock);
    public final ExpireCalls expireCalls = new ExpireCalls(unitOfWork, entries, queues, events, clock);
    public final ListWaitingEntries listWaiting = new ListWaitingEntries(queues, entries);
    public final GetEntry getEntry = new GetEntry(entries);

    public final OrganizationId org = OrganizationId.newId();
    public final Location location = save(locations, Location.create(org, "Matriz", ZoneId.of("America/Sao_Paulo")));
    public final ServiceOffering service = save(services, ServiceOffering.create(org, "GERAL", "Atendimento geral"));
    public final Operator manager = save(operators, Operator.create(org, "Gestora", Set.of(Permission.MANAGE_QUEUES,
            Permission.CHANGE_PRIORITY, Permission.CANCEL_ENTRY, Permission.ASSIGN_RESTRICTED_PRIORITY)));

    public Queue queue(OrderingStrategy ordering) {
        return queue(QueuePolicy.defaults(ordering));
    }

    public Queue queue(QueuePolicy policy) {
        var queue = Queue.create(org, location.id(), "Atendimento", "A", policy, Set.of(service.id()),
                List.of(new PriorityClassification("PREFERENCIAL", "Preferencial", 10, false),
                        new PriorityClassification("URGENTE", "Urgente", 50, true)), clock.instant());
        queue.pullEvents();
        return save(queues, queue);
    }

    public ServicePoint servicePoint(String name) {
        return save(servicePoints, ServicePoint.create(org, location.id(), name));
    }

    /** Operador com permissão de chamada, atribuído à fila em qualquer ponto da unidade. */
    public Operator attendant(Queue queue) {
        var operator = Operator.create(org, "Atendente", Set.of(Permission.CALL_ENTRIES));
        operator.assign(OperatorAssignment.anyServicePoint(queue.id()));
        return save(operators, operator);
    }

    public EntryView enqueue(Queue queue) {
        return enqueue(queue, null);
    }

    public EntryView enqueue(Queue queue, String classification) {
        return enqueueEntry.handle(new EnqueueEntry.Command(org, queue.id(), service.id(), classification, null,
                null, KIOSK));
    }

    public Requester.Staff staff(Operator operator) {
        return new Requester.Staff(operator.id());
    }

    private static <ID, T extends AggregateRoot<ID>> T save(InMemoryRepository<T, ID> repository, T aggregate) {
        repository.save(aggregate);
        return aggregate;
    }
}
