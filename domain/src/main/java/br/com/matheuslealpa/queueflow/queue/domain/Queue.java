package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.location.domain.LocationId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.shared.domain.Actor;
import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;
import br.com.matheuslealpa.queueflow.shared.domain.InvalidStateTransitionException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Agregado de configuração de uma fila. Não contém as entradas: elas formam um agregado
 * próprio ({@code QueueEntry}) para permitir concorrência por entrada e alto volume.
 */
public final class Queue extends AggregateRoot<QueueId> {

    private static final Pattern TICKET_PREFIX = Pattern.compile("[A-Z0-9]{1,5}");

    private final LocationId locationId;
    private String name;
    private final String ticketPrefix;
    private QueueStatus status;
    private QueuePolicy policy;
    private final Set<ServiceOfferingId> acceptedServices;
    private final Map<String, PriorityClassification> classifications;

    private Queue(QueueId id, OrganizationId organizationId, LocationId locationId, String name, String ticketPrefix,
                  QueueStatus status, QueuePolicy policy, Collection<ServiceOfferingId> acceptedServices,
                  Collection<PriorityClassification> classifications, long version) {
        super(id, organizationId, version);
        this.locationId = Guards.required(locationId, "locationId");
        this.name = Guards.maxLength(Guards.notBlank(name, "name"), 150, "name");
        this.ticketPrefix = normalizePrefix(ticketPrefix);
        this.status = Guards.required(status, "status");
        this.policy = Guards.required(policy, "policy");
        this.acceptedServices = new LinkedHashSet<>(acceptedServices);
        this.classifications = new LinkedHashMap<>();
        this.classifications.put(PriorityClassification.NORMAL_CODE, PriorityClassification.NORMAL);
        classifications.forEach(c -> this.classifications.put(c.code(), c));
    }

    public static Queue create(OrganizationId organizationId, LocationId locationId, String name, String ticketPrefix,
                               QueuePolicy policy, Set<ServiceOfferingId> acceptedServices,
                               List<PriorityClassification> classifications, Instant now) {
        var queue = new Queue(QueueId.newId(), organizationId, locationId, name, ticketPrefix, QueueStatus.ACTIVE,
                policy, acceptedServices, classifications, 0);
        queue.registerEvent(new QueueCreated(organizationId, queue.id(), locationId, queue.name,
                policy.ordering(), now));
        return queue;
    }

    public static Queue restore(QueueId id, OrganizationId organizationId, LocationId locationId, String name,
                                String ticketPrefix, QueueStatus status, QueuePolicy policy,
                                Set<ServiceOfferingId> acceptedServices,
                                List<PriorityClassification> classifications, long version) {
        return new Queue(id, organizationId, locationId, name, ticketPrefix, status, policy, acceptedServices,
                classifications, version);
    }

    // ---- regras de ingresso -------------------------------------------------------------

    /**
     * Garante que a fila aceita uma nova entrada para o serviço informado. Uma fila sem
     * serviços configurados aceita somente entradas sem serviço.
     */
    public void ensureAcceptsEntry(ServiceOfferingId serviceOfferingId) {
        if (!status.acceptsEntries()) {
            throw new BusinessRuleViolationException("A fila '%s' não está recebendo entradas (%s)".formatted(name, status));
        }
        if (serviceOfferingId == null) {
            if (!acceptedServices.isEmpty()) {
                throw new BusinessRuleViolationException("A fila '%s' exige a indicação de um serviço".formatted(name));
            }
            return;
        }
        if (!acceptedServices.contains(serviceOfferingId)) {
            throw new BusinessRuleViolationException("A fila '%s' não atende o serviço %s".formatted(name, serviceOfferingId));
        }
    }

    public void ensureAllowsCalls() {
        if (!status.allowsCalls()) {
            throw new BusinessRuleViolationException("A fila '%s' está encerrada".formatted(name));
        }
    }

    /**
     * Converte um código de classificação na prioridade efetiva, validando que a classificação
     * existe nesta fila e que o ator pode atribuí-la. Código ausente resulta em prioridade normal.
     */
    public Priority resolvePriority(String classificationCode, Actor actor) {
        if (classificationCode == null || classificationCode.isBlank()) {
            return Priority.NORMAL;
        }
        var classification = classification(PriorityClassification.normalizeCode(classificationCode))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Classificação de prioridade '%s' não existe na fila '%s'".formatted(classificationCode, name)));
        if (classification.restricted() && !actor.has(Permission.ASSIGN_RESTRICTED_PRIORITY)) {
            throw new NotAuthorizedException(actor, "atribuir a prioridade restrita " + classification.code());
        }
        return classification.toPriority();
    }

    // ---- ciclo de vida ------------------------------------------------------------------

    public void pause(Instant now) {
        changeStatus(QueueStatus.PAUSED, now);
    }

    public void activate(Instant now) {
        changeStatus(QueueStatus.ACTIVE, now);
    }

    public void close(Instant now) {
        changeStatus(QueueStatus.CLOSED, now);
    }

    private void changeStatus(QueueStatus target, Instant now) {
        if (status == target) {
            return;
        }
        if (status == QueueStatus.CLOSED) {
            throw new InvalidStateTransitionException(status, target, "alterar status da fila");
        }
        var previous = status;
        status = target;
        registerEvent(new QueueStatusChanged(organizationId(), id(), previous, target, now));
    }

    // ---- configuração -------------------------------------------------------------------

    public void changePolicy(QueuePolicy newPolicy, Instant now) {
        ensureNotClosed();
        this.policy = Guards.required(newPolicy, "policy");
        registerEvent(new QueuePolicyChanged(organizationId(), id(), newPolicy, now));
    }

    public void rename(String newName) {
        ensureNotClosed();
        this.name = Guards.maxLength(Guards.notBlank(newName, "name"), 150, "name");
    }

    public void defineClassification(PriorityClassification classification) {
        ensureNotClosed();
        classifications.put(classification.code(), classification);
    }

    public void removeClassification(String code) {
        ensureNotClosed();
        var normalized = PriorityClassification.normalizeCode(code);
        if (PriorityClassification.NORMAL_CODE.equals(normalized)) {
            throw new BusinessRuleViolationException("A classificação NORMAL não pode ser removida");
        }
        classifications.remove(normalized);
    }

    public void acceptService(ServiceOfferingId serviceOfferingId) {
        ensureNotClosed();
        acceptedServices.add(Guards.required(serviceOfferingId, "serviceOfferingId"));
    }

    public void stopAcceptingService(ServiceOfferingId serviceOfferingId) {
        ensureNotClosed();
        acceptedServices.remove(serviceOfferingId);
    }

    private void ensureNotClosed() {
        if (status == QueueStatus.CLOSED) {
            throw new BusinessRuleViolationException("A fila '%s' está encerrada".formatted(name));
        }
    }

    private static String normalizePrefix(String prefix) {
        var normalized = Guards.notBlank(prefix, "ticketPrefix").toUpperCase(Locale.ROOT);
        if (!TICKET_PREFIX.matcher(normalized).matches()) {
            throw new BusinessRuleViolationException("ticketPrefix deve ter de 1 a 5 letras ou dígitos");
        }
        return normalized;
    }

    // ---- consultas ----------------------------------------------------------------------

    public Optional<PriorityClassification> classification(String code) {
        return Optional.ofNullable(classifications.get(code));
    }

    public LocationId locationId() {
        return locationId;
    }

    public String name() {
        return name;
    }

    public String ticketPrefix() {
        return ticketPrefix;
    }

    public QueueStatus status() {
        return status;
    }

    public QueuePolicy policy() {
        return policy;
    }

    public EntryOrdering ordering() {
        return policy.ordering().ordering();
    }

    public Set<ServiceOfferingId> acceptedServices() {
        return Set.copyOf(acceptedServices);
    }

    public List<PriorityClassification> classifications() {
        return List.copyOf(classifications.values());
    }
}
