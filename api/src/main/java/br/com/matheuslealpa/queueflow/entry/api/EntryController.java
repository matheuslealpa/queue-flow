package br.com.matheuslealpa.queueflow.entry.api;

import br.com.matheuslealpa.queueflow.entry.application.CancelEntry;
import br.com.matheuslealpa.queueflow.entry.application.ChangeEntryPriority;
import br.com.matheuslealpa.queueflow.entry.application.CompleteService;
import br.com.matheuslealpa.queueflow.entry.application.GetEntry;
import br.com.matheuslealpa.queueflow.entry.application.RegisterNotPresent;
import br.com.matheuslealpa.queueflow.entry.application.ReturnEntryToQueue;
import br.com.matheuslealpa.queueflow.entry.application.StartService;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.shared.api.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ciclo de vida de uma entrada. Cada operação é um comando de negócio (não uma atualização de campos).
 */
@RestController
@RequestMapping("/api/v1/entries/{entryId}")
public class EntryController {

    public record ReasonRequest(@Size(max = 500) String reason) {
    }

    public record ChangePriorityRequest(@NotBlank @Size(max = 40) String priorityClassification) {
    }

    private final GetEntry getEntry;
    private final StartService startService;
    private final CompleteService completeService;
    private final RegisterNotPresent registerNotPresent;
    private final ReturnEntryToQueue returnEntryToQueue;
    private final CancelEntry cancelEntry;
    private final ChangeEntryPriority changeEntryPriority;

    public EntryController(GetEntry getEntry, StartService startService, CompleteService completeService,
                           RegisterNotPresent registerNotPresent, ReturnEntryToQueue returnEntryToQueue,
                           CancelEntry cancelEntry, ChangeEntryPriority changeEntryPriority) {
        this.getEntry = getEntry;
        this.startService = startService;
        this.completeService = completeService;
        this.registerNotPresent = registerNotPresent;
        this.returnEntryToQueue = returnEntryToQueue;
        this.cancelEntry = cancelEntry;
        this.changeEntryPriority = changeEntryPriority;
    }

    @GetMapping
    EntryResponse get(RequestContext context, @PathVariable UUID entryId) {
        return EntryResponse.of(getEntry.handle(context.organizationId(), new QueueEntryId(entryId)));
    }

    @PostMapping("/start")
    EntryResponse start(RequestContext context, @PathVariable UUID entryId) {
        return EntryResponse.of(startService.handle(new StartService.Command(context.organizationId(),
                new QueueEntryId(entryId), context.requireOperator())));
    }

    @PostMapping("/complete")
    EntryResponse complete(RequestContext context, @PathVariable UUID entryId) {
        return EntryResponse.of(completeService.handle(new CompleteService.Command(context.organizationId(),
                new QueueEntryId(entryId), context.requireOperator())));
    }

    /** A pessoa chamada não se apresentou. */
    @PostMapping("/no-show")
    EntryResponse noShow(RequestContext context, @PathVariable UUID entryId) {
        return EntryResponse.of(registerNotPresent.handle(new RegisterNotPresent.Command(context.organizationId(),
                new QueueEntryId(entryId), context.requireOperator())));
    }

    @PostMapping("/return")
    EntryResponse returnToQueue(RequestContext context, @PathVariable UUID entryId,
                                @Valid @RequestBody(required = false) ReasonRequest request) {
        return EntryResponse.of(returnEntryToQueue.handle(new ReturnEntryToQueue.Command(context.organizationId(),
                new QueueEntryId(entryId), context.requireOperator(), reason(request))));
    }

    @PostMapping("/cancel")
    EntryResponse cancel(RequestContext context, @PathVariable UUID entryId,
                         @Valid @RequestBody(required = false) ReasonRequest request) {
        return EntryResponse.of(cancelEntry.handle(new CancelEntry.Command(context.organizationId(),
                new QueueEntryId(entryId), context.requester(), reason(request))));
    }

    @PostMapping("/priority")
    EntryResponse changePriority(RequestContext context, @PathVariable UUID entryId,
                                 @Valid @RequestBody ChangePriorityRequest request) {
        return EntryResponse.of(changeEntryPriority.handle(new ChangeEntryPriority.Command(context.organizationId(),
                new QueueEntryId(entryId), context.requireOperator(), request.priorityClassification())));
    }

    private static String reason(ReasonRequest request) {
        return request == null ? null : request.reason();
    }
}
