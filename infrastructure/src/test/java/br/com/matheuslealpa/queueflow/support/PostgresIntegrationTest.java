package br.com.matheuslealpa.queueflow.support;

import br.com.matheuslealpa.queueflow.InfrastructureTestApplication;
import br.com.matheuslealpa.queueflow.entry.application.CallNextEntry;
import br.com.matheuslealpa.queueflow.entry.application.EnqueueEntry;
import br.com.matheuslealpa.queueflow.entry.application.EntryView;
import br.com.matheuslealpa.queueflow.location.application.CreateLocation;
import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.operator.application.AssignOperator;
import br.com.matheuslealpa.queueflow.operator.application.RegisterOperator;
import br.com.matheuslealpa.queueflow.organization.application.RegisterOrganization;
import br.com.matheuslealpa.queueflow.queue.application.CreateQueue;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.service.application.CreateServiceOffering;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.servicepoint.application.CreateServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.application.Requester;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Base dos testes contra PostgreSQL real. O contêiner é compartilhado entre as classes via cache
 * de contexto; cada teste cria sua própria organização, o que dispensa limpeza do banco.
 */
@SpringBootTest(classes = InfrastructureTestApplication.class)
@Import(TestcontainersConfiguration.class)
public abstract class PostgresIntegrationTest {

    protected static final Requester KIOSK = new Requester.SelfService("kiosk-1");

    @Autowired protected MutableClock clock;
    @Autowired protected RegisterOrganization registerOrganization;
    @Autowired protected CreateLocation createLocation;
    @Autowired protected CreateServiceOffering createServiceOffering;
    @Autowired protected CreateServicePoint createServicePoint;
    @Autowired protected RegisterOperator registerOperator;
    @Autowired protected AssignOperator assignOperator;
    @Autowired protected CreateQueue createQueue;
    @Autowired protected EnqueueEntry enqueueEntry;
    @Autowired protected CallNextEntry callNextEntry;

    /** Organização com unidade, serviço e administrador. */
    protected record Tenant(OrganizationId org, OperatorId admin, LocationId location, ServiceOfferingId service) {
    }

    protected Tenant newTenant() {
        var created = registerOrganization.handle(new RegisterOrganization.Command("Org de teste", "Admin"));
        var org = new OrganizationId(created.organizationId());
        var admin = new OperatorId(created.adminOperatorId());
        var location = createLocation.handle(new CreateLocation.Command(org, admin, "Matriz",
                ZoneId.of("America/Sao_Paulo")));
        var service = createServiceOffering.handle(new CreateServiceOffering.Command(org, admin, "GERAL", "Geral"));
        return new Tenant(org, admin, new LocationId(location.id()), new ServiceOfferingId(service.id()));
    }

    protected QueueId newQueue(Tenant tenant, OrderingStrategy ordering) {
        var view = createQueue.handle(new CreateQueue.Command(tenant.org(), tenant.admin(), tenant.location(),
                "Atendimento", "A", QueuePolicy.defaults(ordering), Set.of(tenant.service()),
                List.of(new PriorityClassification("PREFERENCIAL", "Preferencial", 10, false))));
        return new QueueId(view.id());
    }

    protected ServicePointId newServicePoint(Tenant tenant, String name) {
        return new ServicePointId(createServicePoint.handle(new CreateServicePoint.Command(tenant.org(),
                tenant.admin(), tenant.location(), name)).id());
    }

    protected OperatorId newAttendant(Tenant tenant, QueueId queue) {
        var operator = registerOperator.handle(new RegisterOperator.Command(tenant.org(), tenant.admin(), "Atendente",
                Set.of(Permission.CALL_ENTRIES)));
        var id = new OperatorId(operator.id());
        assignOperator.handle(new AssignOperator.Command(tenant.org(), tenant.admin(), id, queue, null));
        return id;
    }

    protected EntryView enqueue(Tenant tenant, QueueId queue, String classification) {
        return enqueueEntry.handle(new EnqueueEntry.Command(tenant.org(), queue, tenant.service(), classification,
                null, null, KIOSK));
    }

    protected Optional<EntryView> callNext(Tenant tenant, QueueId queue, OperatorId operator,
                                           ServicePointId servicePoint) {
        return callNextEntry.handle(new CallNextEntry.Command(tenant.org(), queue, operator, servicePoint));
    }
}
