package br.com.matheuslealpa.queueflow.entry.api;

import br.com.matheuslealpa.queueflow.entry.application.CallNextEntry;
import br.com.matheuslealpa.queueflow.entry.application.EnqueueEntry;
import br.com.matheuslealpa.queueflow.entry.application.ListWaitingEntries;
import br.com.matheuslealpa.queueflow.entry.domain.ExternalReference;
import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.api.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Operações sobre as entradas de uma fila: ingresso, consulta da espera e chamada da próxima.
 */
@Validated
@RestController
@RequestMapping("/api/v1/queues/{queueId}")
public class QueueEntryController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    /** @param priorityClassification código configurado na fila; a fila define o nível */
    public record EnqueueRequest(UUID serviceId,
                                 @Size(max = 40) String priorityClassification,
                                 @Size(max = 200) String externalReference) {
    }

    public record CallNextRequest(@NotNull UUID servicePointId) {
    }

    private final EnqueueEntry enqueueEntry;
    private final ListWaitingEntries listWaitingEntries;
    private final CallNextEntry callNextEntry;

    public QueueEntryController(EnqueueEntry enqueueEntry, ListWaitingEntries listWaitingEntries,
                                CallNextEntry callNextEntry) {
        this.enqueueEntry = enqueueEntry;
        this.listWaitingEntries = listWaitingEntries;
        this.callNextEntry = callNextEntry;
    }

    @PostMapping("/entries")
    ResponseEntity<EntryResponse> enqueue(RequestContext context, @PathVariable UUID queueId,
                                          @RequestHeader(name = IDEMPOTENCY_HEADER, required = false)
                                          @Size(max = 100) String idempotencyKey,
                                          @Valid @RequestBody EnqueueRequest request) {
        var view = enqueueEntry.handle(new EnqueueEntry.Command(context.organizationId(), new QueueId(queueId),
                request.serviceId() == null ? null : new ServiceOfferingId(request.serviceId()),
                request.priorityClassification(),
                isBlank(request.externalReference()) ? null : new ExternalReference(request.externalReference()),
                isBlank(idempotencyKey) ? null : new IdempotencyKey(idempotencyKey),
                context.requester()));
        return ResponseEntity.created(URI.create("/api/v1/entries/" + view.id())).body(EntryResponse.of(view));
    }

    @GetMapping("/entries")
    List<EntryResponse> waiting(RequestContext context, @PathVariable UUID queueId,
                                @RequestParam(defaultValue = "0") @Min(0) int offset,
                                @RequestParam(defaultValue = "50") @Min(1) @Max(ListWaitingEntries.MAX_PAGE_SIZE) int limit) {
        return listWaitingEntries.handle(context.organizationId(), new QueueId(queueId), offset, limit).stream()
                .map(EntryResponse::of)
                .toList();
    }

    /** 200 com a entrada chamada, ou 204 se ninguém estiver aguardando. */
    @PostMapping("/calls/next")
    ResponseEntity<EntryResponse> callNext(RequestContext context, @PathVariable UUID queueId,
                                           @Valid @RequestBody CallNextRequest request) {
        return callNextEntry.handle(new CallNextEntry.Command(context.organizationId(), new QueueId(queueId),
                        context.requireOperator(), new ServicePointId(request.servicePointId())))
                .map(view -> ResponseEntity.ok(EntryResponse.of(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
