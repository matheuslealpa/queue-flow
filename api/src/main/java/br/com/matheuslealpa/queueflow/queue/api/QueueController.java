package br.com.matheuslealpa.queueflow.queue.api;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.queue.application.ChangeQueueStatus;
import br.com.matheuslealpa.queueflow.queue.application.CreateQueue;
import br.com.matheuslealpa.queueflow.queue.application.GetQueue;
import br.com.matheuslealpa.queueflow.queue.domain.OrderingStrategy;
import br.com.matheuslealpa.queueflow.queue.domain.PriorityClassification;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.QueueStatus;
import br.com.matheuslealpa.queueflow.queue.domain.ReturnPosition;
import br.com.matheuslealpa.queueflow.reporting.application.GetQueueMetrics;
import br.com.matheuslealpa.queueflow.reporting.application.QueueMetrics;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.shared.api.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/queues")
public class QueueController {

    public record CreateQueueRequest(@NotNull UUID locationId,
                                     @NotBlank @Size(max = 150) String name,
                                     @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,5}") String ticketPrefix,
                                     @NotNull @Valid PolicyRequest policy,
                                     Set<UUID> acceptedServices,
                                     List<@Valid ClassificationRequest> classifications) {
    }

    public record PolicyRequest(@NotNull OrderingStrategy ordering,
                                @NotNull @Min(10) @Max(3600) Integer callTimeoutSeconds,
                                @NotNull @Min(1) @Max(20) Integer maxCallAttempts,
                                @NotNull ReturnPosition returnPosition,
                                boolean allowReturnFromService,
                                @Min(0) @Max(QueuePolicy.MAX_INTERLEAVING) Integer maxConsecutivePriorityCalls) {
    }

    public record ClassificationRequest(@NotBlank @Size(max = 40) String code,
                                        @NotBlank @Size(max = 100) String label,
                                        @Min(0) @Max(100) int level,
                                        boolean restricted) {
    }

    public record ChangeStatusRequest(@NotNull QueueStatus status) {
    }

    private final CreateQueue createQueue;
    private final GetQueue getQueue;
    private final ChangeQueueStatus changeQueueStatus;
    private final GetQueueMetrics getQueueMetrics;
    private final QueueEventStreams eventStreams;

    public QueueController(CreateQueue createQueue, GetQueue getQueue, ChangeQueueStatus changeQueueStatus,
                           GetQueueMetrics getQueueMetrics, QueueEventStreams eventStreams) {
        this.createQueue = createQueue;
        this.getQueue = getQueue;
        this.changeQueueStatus = changeQueueStatus;
        this.getQueueMetrics = getQueueMetrics;
        this.eventStreams = eventStreams;
    }

    @PostMapping
    ResponseEntity<QueueResponse> create(RequestContext context, @Valid @RequestBody CreateQueueRequest request) {
        var policy = request.policy();
        var services = request.acceptedServices() == null ? Set.<ServiceOfferingId>of()
                : request.acceptedServices().stream().map(ServiceOfferingId::new).collect(Collectors.toSet());
        var classifications = request.classifications() == null ? List.<PriorityClassification>of()
                : request.classifications().stream()
                .map(c -> new PriorityClassification(c.code(), c.label(), c.level(), c.restricted()))
                .toList();
        var view = createQueue.handle(new CreateQueue.Command(context.organizationId(), context.requireOperator(),
                new LocationId(request.locationId()), request.name(), request.ticketPrefix(),
                new QueuePolicy(policy.ordering(), Duration.ofSeconds(policy.callTimeoutSeconds()),
                        policy.maxCallAttempts(), policy.returnPosition(), policy.allowReturnFromService(),
                        policy.maxConsecutivePriorityCalls() == null ? 0 : policy.maxConsecutivePriorityCalls()),
                services, classifications));
        return ResponseEntity.created(URI.create("/api/v1/queues/" + view.id())).body(QueueResponse.of(view));
    }

    @GetMapping("/{queueId}")
    QueueResponse get(RequestContext context, @PathVariable UUID queueId) {
        return QueueResponse.of(getQueue.handle(context.organizationId(), new QueueId(queueId)));
    }

    @PutMapping("/{queueId}/status")
    QueueResponse changeStatus(RequestContext context, @PathVariable UUID queueId,
                               @Valid @RequestBody ChangeStatusRequest request) {
        return QueueResponse.of(changeQueueStatus.handle(new ChangeQueueStatus.Command(context.organizationId(),
                context.requireOperator(), new QueueId(queueId), request.status())));
    }

    /**
     * Mudanças na fila em tempo real (Server-Sent Events). Mesma identificação das demais rotas;
     * a fila é consultada antes para validar existência e organização.
     */
    @GetMapping(path = "/{queueId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(RequestContext context, @PathVariable UUID queueId) {
        var id = new QueueId(queueId);
        getQueue.handle(context.organizationId(), id);
        return eventStreams.open(context.organizationId(), id);
    }

    @GetMapping("/{queueId}/metrics")
    QueueMetrics metrics(RequestContext context, @PathVariable UUID queueId) {
        return getQueueMetrics.handle(context.organizationId(), new QueueId(queueId));
    }
}
