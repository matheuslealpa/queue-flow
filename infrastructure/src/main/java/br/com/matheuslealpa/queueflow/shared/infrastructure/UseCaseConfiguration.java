package br.com.matheuslealpa.queueflow.shared.infrastructure;

import br.com.matheuslealpa.queueflow.entry.application.CallNextEntry;
import br.com.matheuslealpa.queueflow.entry.application.CancelEntry;
import br.com.matheuslealpa.queueflow.entry.application.ChangeEntryPriority;
import br.com.matheuslealpa.queueflow.entry.application.CompleteService;
import br.com.matheuslealpa.queueflow.entry.application.EnqueueEntry;
import br.com.matheuslealpa.queueflow.entry.application.ExpireCalls;
import br.com.matheuslealpa.queueflow.entry.application.GetEntry;
import br.com.matheuslealpa.queueflow.entry.application.ListWaitingEntries;
import br.com.matheuslealpa.queueflow.entry.application.RegisterNotPresent;
import br.com.matheuslealpa.queueflow.entry.application.ReturnEntryToQueue;
import br.com.matheuslealpa.queueflow.entry.application.StartService;
import br.com.matheuslealpa.queueflow.entry.application.TicketSequence;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.location.application.CreateLocation;
import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.operator.application.ActorResolver;
import br.com.matheuslealpa.queueflow.operator.application.AssignOperator;
import br.com.matheuslealpa.queueflow.operator.application.RegisterOperator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.organization.application.RegisterOrganization;
import br.com.matheuslealpa.queueflow.organization.domain.OrganizationRepository;
import br.com.matheuslealpa.queueflow.queue.application.ChangeQueueStatus;
import br.com.matheuslealpa.queueflow.queue.application.CreateQueue;
import br.com.matheuslealpa.queueflow.queue.application.GetQueue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueRepository;
import br.com.matheuslealpa.queueflow.reporting.application.GetQueueMetrics;
import br.com.matheuslealpa.queueflow.reporting.application.QueueMetricsReader;
import br.com.matheuslealpa.queueflow.service.application.CreateServiceOffering;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingRepository;
import br.com.matheuslealpa.queueflow.servicepoint.application.CreateServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import br.com.matheuslealpa.queueflow.shared.application.DomainEventPublisher;
import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Registra os casos de uso (classes sem dependência de Spring) como beans, ligando-os aos adapters.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {

    @Bean
    ActorResolver actorResolver(OperatorRepository operators) {
        return new ActorResolver(operators);
    }

    // ---- cadastros ----------------------------------------------------------------------

    @Bean
    RegisterOrganization registerOrganization(UnitOfWork uow, OrganizationRepository organizations,
                                              OperatorRepository operators) {
        return new RegisterOrganization(uow, organizations, operators);
    }

    @Bean
    CreateLocation createLocation(UnitOfWork uow, LocationRepository locations, ActorResolver actors) {
        return new CreateLocation(uow, locations, actors);
    }

    @Bean
    CreateServiceOffering createServiceOffering(UnitOfWork uow, ServiceOfferingRepository services,
                                                ActorResolver actors) {
        return new CreateServiceOffering(uow, services, actors);
    }

    @Bean
    CreateServicePoint createServicePoint(UnitOfWork uow, ServicePointRepository servicePoints,
                                          LocationRepository locations, ActorResolver actors) {
        return new CreateServicePoint(uow, servicePoints, locations, actors);
    }

    @Bean
    RegisterOperator registerOperator(UnitOfWork uow, OperatorRepository operators, ActorResolver actors) {
        return new RegisterOperator(uow, operators, actors);
    }

    @Bean
    AssignOperator assignOperator(UnitOfWork uow, OperatorRepository operators, QueueRepository queues,
                                  ServicePointRepository servicePoints, ActorResolver actors) {
        return new AssignOperator(uow, operators, queues, servicePoints, actors);
    }

    // ---- filas --------------------------------------------------------------------------

    @Bean
    CreateQueue createQueue(UnitOfWork uow, QueueRepository queues, LocationRepository locations,
                            ServiceOfferingRepository services, ActorResolver actors, DomainEventPublisher events,
                            Clock clock) {
        return new CreateQueue(uow, queues, locations, services, actors, events, clock);
    }

    @Bean
    ChangeQueueStatus changeQueueStatus(UnitOfWork uow, QueueRepository queues, ActorResolver actors,
                                        DomainEventPublisher events, Clock clock) {
        return new ChangeQueueStatus(uow, queues, actors, events, clock);
    }

    @Bean
    GetQueue getQueue(QueueRepository queues) {
        return new GetQueue(queues);
    }

    @Bean
    GetQueueMetrics getQueueMetrics(QueueRepository queues, LocationRepository locations, QueueMetricsReader reader,
                                    Clock clock) {
        return new GetQueueMetrics(queues, locations, reader, clock);
    }

    // ---- entradas -----------------------------------------------------------------------

    @Bean
    EnqueueEntry enqueueEntry(UnitOfWork uow, QueueRepository queues, QueueEntryRepository entries,
                              LocationRepository locations, ServiceOfferingRepository services,
                              TicketSequence tickets, ActorResolver actors, DomainEventPublisher events, Clock clock) {
        return new EnqueueEntry(uow, queues, entries, locations, services, tickets, actors, events, clock);
    }

    @Bean
    CallNextEntry callNextEntry(UnitOfWork uow, QueueRepository queues, QueueEntryRepository entries,
                                ServicePointRepository servicePoints, ActorResolver actors,
                                DomainEventPublisher events, Clock clock) {
        return new CallNextEntry(uow, queues, entries, servicePoints, actors, events, clock);
    }

    @Bean
    StartService startService(UnitOfWork uow, QueueEntryRepository entries, QueueRepository queues,
                              ActorResolver actors, DomainEventPublisher events, Clock clock) {
        return new StartService(uow, entries, queues, actors, events, clock);
    }

    @Bean
    CompleteService completeService(UnitOfWork uow, QueueEntryRepository entries, QueueRepository queues,
                                    ActorResolver actors, DomainEventPublisher events, Clock clock) {
        return new CompleteService(uow, entries, queues, actors, events, clock);
    }

    @Bean
    RegisterNotPresent registerNotPresent(UnitOfWork uow, QueueEntryRepository entries, QueueRepository queues,
                                          ActorResolver actors, DomainEventPublisher events, Clock clock) {
        return new RegisterNotPresent(uow, entries, queues, actors, events, clock);
    }

    @Bean
    ReturnEntryToQueue returnEntryToQueue(UnitOfWork uow, QueueEntryRepository entries, QueueRepository queues,
                                          ActorResolver actors, DomainEventPublisher events, Clock clock) {
        return new ReturnEntryToQueue(uow, entries, queues, actors, events, clock);
    }

    @Bean
    CancelEntry cancelEntry(UnitOfWork uow, QueueEntryRepository entries, QueueRepository queues,
                            ActorResolver actors, DomainEventPublisher events, Clock clock) {
        return new CancelEntry(uow, entries, queues, actors, events, clock);
    }

    @Bean
    ChangeEntryPriority changeEntryPriority(UnitOfWork uow, QueueEntryRepository entries, QueueRepository queues,
                                            ActorResolver actors, DomainEventPublisher events, Clock clock) {
        return new ChangeEntryPriority(uow, entries, queues, actors, events, clock);
    }

    @Bean
    ExpireCalls expireCalls(UnitOfWork uow, QueueEntryRepository entries, QueueRepository queues,
                            DomainEventPublisher events, Clock clock) {
        return new ExpireCalls(uow, entries, queues, events, clock);
    }

    @Bean
    ListWaitingEntries listWaitingEntries(QueueRepository queues, QueueEntryRepository entries) {
        return new ListWaitingEntries(queues, entries);
    }

    @Bean
    GetEntry getEntry(QueueEntryRepository entries) {
        return new GetEntry(entries);
    }
}
