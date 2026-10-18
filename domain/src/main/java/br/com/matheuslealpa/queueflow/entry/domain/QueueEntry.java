package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.queue.domain.Priority;
import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.queue.domain.QueuePolicy;
import br.com.matheuslealpa.queueflow.queue.domain.ReturnPosition;
import br.com.matheuslealpa.queueflow.queue.domain.WaitingPosition;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.Actor;
import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;
import br.com.matheuslealpa.queueflow.shared.domain.InvalidStateTransitionException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Agregado que representa a solicitação de uma pessoa em uma fila. Controla a máquina de
 * estados, as chamadas, as sessões de atendimento e o histórico de operações.
 *
 * <p>É um agregado separado de {@link Queue}: cada entrada é bloqueada individualmente ao ser
 * chamada, e a fila nunca carrega suas entradas. Operações que dependem da configuração da fila
 * recebem o agregado {@link Queue} como argumento.
 */
public final class QueueEntry extends AggregateRoot<QueueEntryId> {

    private static final Actor CALL_EXPIRATION = new Actor.SystemProcess("call-expiration");

    private final QueueId queueId;
    private final ServiceOfferingId serviceOfferingId;
    private final TicketNumber ticket;
    private final ExternalReference externalReference;
    private final IdempotencyKey idempotencyKey;
    private final Instant createdAt;
    private Priority priority;
    private EntryStatus status;
    private Instant enqueuedAt;
    private CurrentCall currentCall;
    private int missedCalls;
    private final List<ServiceSession> sessions;
    private final List<EntryHistoryRecord> history;

    private QueueEntry(Snapshot state) {
        super(state.id(), state.organizationId(), state.version());
        this.queueId = Guards.required(state.queueId(), "queueId");
        this.serviceOfferingId = state.serviceOfferingId();
        this.ticket = Guards.required(state.ticket(), "ticket");
        this.externalReference = state.externalReference();
        this.idempotencyKey = state.idempotencyKey();
        this.createdAt = Guards.required(state.createdAt(), "createdAt");
        this.priority = Guards.required(state.priority(), "priority");
        this.status = Guards.required(state.status(), "status");
        this.enqueuedAt = Guards.required(state.enqueuedAt(), "enqueuedAt");
        this.currentCall = state.currentCall();
        this.missedCalls = state.missedCalls();
        this.sessions = new ArrayList<>(state.sessions());
        this.history = new ArrayList<>(state.history());
        checkConsistency();
    }

    /**
     * Cria uma entrada aguardando atendimento. Valida que a fila aceita o serviço solicitado e
     * resolve a prioridade a partir das classificações da fila.
     */
    public static QueueEntry create(Queue queue, NewEntry request, TicketNumber ticket, Actor actor, Instant now) {
        Objects.requireNonNull(request, "request");
        queue.ensureAcceptsEntry(request.serviceOfferingId());
        if (!queue.ticketPrefix().equals(ticket.prefix())) {
            throw new BusinessRuleViolationException("A senha %s não pertence à fila '%s'".formatted(ticket, queue.name()));
        }
        var priority = queue.resolvePriority(request.priorityClassification(), actor);
        var entry = new QueueEntry(new Snapshot(QueueEntryId.newId(), queue.organizationId(), queue.id(),
                request.serviceOfferingId(), ticket, request.externalReference(), request.idempotencyKey(), priority,
                EntryStatus.WAITING, now, now, null, 0, List.of(), List.of(), 0));
        entry.history.add(new EntryHistoryRecord(EntryAction.CREATED, null, EntryStatus.WAITING, actor.describe(),
                priority.classificationCode(), now));
        entry.registerEvent(new EntryCreated(entry.organizationId(), entry.id(), entry.queueId, ticket,
                entry.serviceOfferingId, priority, now));
        return entry;
    }

    public static QueueEntry restore(Snapshot state) {
        return new QueueEntry(state);
    }

    // ---- chamada ------------------------------------------------------------------------

    /** Reserva a entrada para o operador, que a chama para o ponto de atendimento indicado. */
    public void call(Queue queue, ServicePointId servicePointId, Actor actor, Instant now) {
        ensureSameQueue(queue);
        queue.ensureAllowsCalls();
        ensureCanTransition(EntryStatus.CALLED, "chamar");
        var operatorId = requireStaffWith(actor, Permission.CALL_ENTRIES, "chamar entradas");
        var expiresAt = now.plus(queue.policy().callTimeout());
        currentCall = new CurrentCall(operatorId, Guards.required(servicePointId, "servicePointId"), now, expiresAt);
        moveTo(EntryStatus.CALLED, EntryAction.CALLED, actor, null, now);
        registerEvent(new EntryCalled(organizationId(), id(), queueId, ticket, operatorId, servicePointId,
                expiresAt, now));
    }

    /** O operador que chamou informa que a pessoa não se apresentou. */
    public void registerNotPresent(Queue queue, Actor actor, Instant now) {
        ensureSameQueue(queue);
        ensureStatus(EntryStatus.CALLED, "registrar não comparecimento");
        requireCaller(actor, "registrar não comparecimento");
        handleMissedCall(queue.policy(), MissedCallReason.NOT_PRESENT, actor, now);
    }

    /** Expira a chamada cujo prazo de comparecimento terminou. */
    public void expireCall(Queue queue, Instant now) {
        ensureSameQueue(queue);
        ensureStatus(EntryStatus.CALLED, "expirar chamada");
        if (!currentCall.isExpiredAt(now)) {
            throw new BusinessRuleViolationException("A chamada da senha %s expira apenas em %s"
                    .formatted(ticket, currentCall.expiresAt()));
        }
        handleMissedCall(queue.policy(), MissedCallReason.CALL_EXPIRED, CALL_EXPIRATION, now);
    }

    private void handleMissedCall(QueuePolicy policy, MissedCallReason reason, Actor actor, Instant now) {
        missedCalls++;
        currentCall = null;
        var action = reason == MissedCallReason.CALL_EXPIRED ? EntryAction.CALL_EXPIRED : EntryAction.NOT_PRESENT;
        registerEvent(new EntryCallMissed(organizationId(), id(), queueId, reason, missedCalls, now));
        if (missedCalls >= policy.maxCallAttempts()) {
            moveTo(EntryStatus.NO_SHOW, action, actor, "chamadas sem comparecimento: " + missedCalls, now);
            registerEvent(new EntryMarkedNoShow(organizationId(), id(), queueId, missedCalls, now));
        } else {
            reposition(policy.returnPosition(), now);
            moveTo(EntryStatus.WAITING, action, actor, "chamadas sem comparecimento: " + missedCalls, now);
            registerEvent(new EntryReturnedToQueue(organizationId(), id(), queueId, ReturnReason.MISSED_CALL, now));
        }
    }

    // ---- atendimento --------------------------------------------------------------------

    /** A pessoa se apresentou: abre uma sessão de atendimento com o operador que a chamou. */
    public void startService(Actor actor, Instant now) {
        ensureCanTransition(EntryStatus.IN_SERVICE, "iniciar atendimento");
        var operatorId = requireCaller(actor, "iniciar o atendimento");
        var session = ServiceSession.open(operatorId, currentCall.servicePointId(), now);
        sessions.add(session);
        currentCall = null;
        moveTo(EntryStatus.IN_SERVICE, EntryAction.SERVICE_STARTED, actor, null, now);
        registerEvent(new ServiceStarted(organizationId(), id(), queueId, session.id(), operatorId,
                session.servicePointId(), now));
    }

    public void complete(Actor actor, Instant now) {
        ensureCanTransition(EntryStatus.COMPLETED, "concluir atendimento");
        var session = requireSessionOperator(actor, "concluir o atendimento");
        session.close(SessionOutcome.COMPLETED, now);
        moveTo(EntryStatus.COMPLETED, EntryAction.COMPLETED, actor, null, now);
        registerEvent(new ServiceCompleted(organizationId(), id(), queueId, session.id(), session.operatorId(),
                session.duration().orElse(Duration.ZERO), now));
    }

    /** Devolve à fila uma entrada em atendimento, quando a política da fila permite. */
    public void returnToQueue(Queue queue, Actor actor, String reason, Instant now) {
        ensureSameQueue(queue);
        ensureStatus(EntryStatus.IN_SERVICE, "devolver à fila");
        if (!queue.policy().allowReturnFromService()) {
            throw new BusinessRuleViolationException("A fila '%s' não permite devolver entradas em atendimento"
                    .formatted(queue.name()));
        }
        var session = requireSessionOperator(actor, "devolver a entrada à fila");
        session.close(SessionOutcome.RETURNED_TO_QUEUE, now);
        reposition(queue.policy().returnPosition(), now);
        moveTo(EntryStatus.WAITING, EntryAction.RETURNED_TO_QUEUE, actor, reason, now);
        registerEvent(new EntryReturnedToQueue(organizationId(), id(), queueId, ReturnReason.OPERATOR_REQUEST, now));
    }

    /**
     * Cancela a entrada. Exige {@code CANCEL_ENTRY}, exceto para a própria pessoa (autoatendimento)
     * desistindo enquanto aguarda, ou para processos da plataforma.
     */
    public void cancel(Actor actor, String reason, Instant now) {
        ensureCanTransition(EntryStatus.CANCELLED, "cancelar");
        var allowed = actor.has(Permission.CANCEL_ENTRY)
                || actor instanceof Actor.SystemProcess
                || (actor instanceof Actor.SelfService && status == EntryStatus.WAITING);
        if (!allowed) {
            throw new NotAuthorizedException(actor, "cancelar a senha " + ticket);
        }
        openSession().ifPresent(session -> session.close(SessionOutcome.CANCELLED, now));
        currentCall = null;
        var previous = status;
        moveTo(EntryStatus.CANCELLED, EntryAction.CANCELLED, actor, reason, now);
        registerEvent(new EntryCancelled(organizationId(), id(), queueId, previous, reason, now));
    }

    // ---- prioridade ---------------------------------------------------------------------

    /** Reavalia a prioridade de uma entrada em espera. */
    public void changePriority(Queue queue, String classificationCode, Actor actor, Instant now) {
        ensureSameQueue(queue);
        if (status != EntryStatus.WAITING) {
            throw new BusinessRuleViolationException("A prioridade só pode ser alterada enquanto a entrada aguarda");
        }
        if (!actor.has(Permission.CHANGE_PRIORITY)) {
            throw new NotAuthorizedException(actor, "alterar a prioridade da senha " + ticket);
        }
        var newPriority = queue.resolvePriority(classificationCode, actor);
        if (newPriority.equals(priority)) {
            return;
        }
        var previous = priority;
        priority = newPriority;
        history.add(new EntryHistoryRecord(EntryAction.PRIORITY_CHANGED, status, status, actor.describe(),
                previous.classificationCode() + " -> " + newPriority.classificationCode(), now));
        registerEvent(new EntryPriorityChanged(organizationId(), id(), queueId, previous, newPriority, now));
    }

    // ---- auxiliares ---------------------------------------------------------------------

    private void ensureSameQueue(Queue queue) {
        if (!queue.id().equals(queueId) || !queue.belongsTo(organizationId())) {
            throw new IllegalArgumentException("A fila informada não é a fila da entrada " + id());
        }
    }

    private void ensureCanTransition(EntryStatus target, String action) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(status, target, action);
        }
    }

    private void ensureStatus(EntryStatus expected, String action) {
        if (status != expected) {
            throw new BusinessRuleViolationException("Não é possível %s: a senha %s está em %s, esperado %s"
                    .formatted(action, ticket, status, expected));
        }
    }

    private void moveTo(EntryStatus target, EntryAction action, Actor actor, String detail, Instant now) {
        var previous = status;
        status = target;
        history.add(new EntryHistoryRecord(action, previous, target, actor.describe(), detail, now));
    }

    private void reposition(ReturnPosition position, Instant now) {
        if (position == ReturnPosition.END_OF_QUEUE) {
            enqueuedAt = now;
        }
    }

    private static OperatorId requireStaffWith(Actor actor, Permission permission, String action) {
        if (actor instanceof Actor.Staff staff && staff.has(permission)) {
            return staff.operatorId();
        }
        throw new NotAuthorizedException(actor, action);
    }

    /** Somente o operador que fez a chamada pode dar continuidade a ela. */
    private OperatorId requireCaller(Actor actor, String action) {
        if (actor instanceof Actor.Staff staff && staff.operatorId().equals(currentCall.operatorId())) {
            return staff.operatorId();
        }
        throw new NotAuthorizedException(actor, action + " da senha " + ticket);
    }

    private ServiceSession requireSessionOperator(Actor actor, String action) {
        var session = openSession().orElseThrow();
        if (actor instanceof Actor.Staff staff && staff.operatorId().equals(session.operatorId())) {
            return session;
        }
        throw new NotAuthorizedException(actor, action + " da senha " + ticket);
    }

    private void checkConsistency() {
        if ((status == EntryStatus.CALLED) != (currentCall != null)) {
            throw new IllegalStateException("Entrada %s: chamada corrente inconsistente com status %s".formatted(id(), status));
        }
        var open = sessions.stream().filter(ServiceSession::isOpen).count();
        if (open > 1 || (status == EntryStatus.IN_SERVICE) != (open == 1)) {
            throw new IllegalStateException("Entrada %s: sessões abertas inconsistentes com status %s".formatted(id(), status));
        }
        if (missedCalls < 0) {
            throw new IllegalStateException("missedCalls negativo");
        }
    }

    // ---- consultas ----------------------------------------------------------------------

    public WaitingPosition waitingPosition() {
        return new WaitingPosition(priority.level(), enqueuedAt, ticket.sequence());
    }

    public Optional<ServiceSession> openSession() {
        return sessions.stream().filter(ServiceSession::isOpen).findFirst();
    }

    public QueueId queueId() {
        return queueId;
    }

    public Optional<ServiceOfferingId> serviceOfferingId() {
        return Optional.ofNullable(serviceOfferingId);
    }

    public TicketNumber ticket() {
        return ticket;
    }

    public Optional<ExternalReference> externalReference() {
        return Optional.ofNullable(externalReference);
    }

    public Optional<IdempotencyKey> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Priority priority() {
        return priority;
    }

    public EntryStatus status() {
        return status;
    }

    public Instant enqueuedAt() {
        return enqueuedAt;
    }

    public Optional<CurrentCall> currentCall() {
        return Optional.ofNullable(currentCall);
    }

    public int missedCalls() {
        return missedCalls;
    }

    public List<ServiceSession> sessions() {
        return Collections.unmodifiableList(sessions);
    }

    public List<EntryHistoryRecord> history() {
        return Collections.unmodifiableList(history);
    }

    /**
     * Estado completo para reconstituição a partir da persistência. As invariantes entre status,
     * chamada corrente e sessões abertas são verificadas em {@link #restore(Snapshot)}.
     */
    public record Snapshot(QueueEntryId id,
                           OrganizationId organizationId,
                           QueueId queueId,
                           ServiceOfferingId serviceOfferingId,
                           TicketNumber ticket,
                           ExternalReference externalReference,
                           IdempotencyKey idempotencyKey,
                           Priority priority,
                           EntryStatus status,
                           Instant createdAt,
                           Instant enqueuedAt,
                           CurrentCall currentCall,
                           int missedCalls,
                           List<ServiceSession> sessions,
                           List<EntryHistoryRecord> history,
                           long version) {

        public Snapshot {
            sessions = List.copyOf(sessions);
            history = List.copyOf(history);
        }
    }
}
