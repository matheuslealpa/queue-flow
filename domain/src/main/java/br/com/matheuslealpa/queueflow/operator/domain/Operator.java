package br.com.matheuslealpa.queueflow.operator.domain;

import br.com.matheuslealpa.queueflow.queue.domain.Queue;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.Actor;
import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.domain.Permission;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Profissional que realiza atendimentos. Concentra as permissões e as filas que pode atender.
 */
public final class Operator extends AggregateRoot<OperatorId> {

    private String name;
    private boolean active;
    private final Set<Permission> permissions;
    private final Set<OperatorAssignment> assignments;

    private Operator(OperatorId id, OrganizationId organizationId, String name, boolean active,
                     Collection<Permission> permissions, Collection<OperatorAssignment> assignments, long version) {
        super(id, organizationId, version);
        this.name = Guards.maxLength(Guards.notBlank(name, "name"), 150, "name");
        this.active = active;
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
        this.assignments = new LinkedHashSet<>(assignments);
    }

    public static Operator create(OrganizationId organizationId, String name, Set<Permission> permissions) {
        return new Operator(OperatorId.newId(), organizationId, name, true, permissions, Set.of(), 0);
    }

    public static Operator restore(OperatorId id, OrganizationId organizationId, String name, boolean active,
                                   Set<Permission> permissions, Set<OperatorAssignment> assignments, long version) {
        return new Operator(id, organizationId, name, active, permissions, assignments, version);
    }

    public void grant(Permission permission) {
        permissions.add(permission);
    }

    public void revoke(Permission permission) {
        permissions.remove(permission);
    }

    public void assign(OperatorAssignment assignment) {
        assignments.add(Guards.required(assignment, "assignment"));
    }

    public void unassign(OperatorAssignment assignment) {
        assignments.remove(assignment);
    }

    public boolean canServe(QueueId queueId, ServicePointId servicePointId) {
        return active && assignments.stream().anyMatch(a -> a.covers(queueId, servicePointId));
    }

    /**
     * Garante que o operador pode chamar entradas da fila a partir do ponto de atendimento:
     * operador ativo, com permissão e atribuição, e ponto ativo na mesma unidade da fila.
     */
    public void ensureCanServe(Queue queue, ServicePoint servicePoint) {
        if (!servicePoint.isActive() || !servicePoint.belongsTo(organizationId())
                || !servicePoint.locationId().equals(queue.locationId())) {
            throw new BusinessRuleViolationException("O ponto de atendimento '%s' não pode atender a fila '%s'"
                    .formatted(servicePoint.name(), queue.name()));
        }
        if (!permissions.contains(Permission.CALL_ENTRIES) || !canServe(queue.id(), servicePoint.id())) {
            throw new NotAuthorizedException(toActor(), "atender a fila '%s' no ponto '%s'"
                    .formatted(queue.name(), servicePoint.name()));
        }
    }

    /** Fotografia das permissões atuais, usada para autorizar uma operação de domínio. */
    public Actor.Staff toActor() {
        return new Actor.Staff(id(), active ? permissions : Set.of());
    }

    public void rename(String newName) {
        this.name = Guards.maxLength(Guards.notBlank(newName, "name"), 150, "name");
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public String name() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }

    public Set<OperatorAssignment> assignments() {
        return Set.copyOf(assignments);
    }
}
