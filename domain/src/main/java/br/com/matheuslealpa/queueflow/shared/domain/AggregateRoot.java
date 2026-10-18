package br.com.matheuslealpa.queueflow.shared.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Raiz de agregado: identidade, tenant, versão para controle otimista e eventos pendentes.
 */
public abstract class AggregateRoot<ID> {

    private final ID id;
    private final OrganizationId organizationId;
    private final long version;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    protected AggregateRoot(ID id, OrganizationId organizationId, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.version = version;
    }

    public ID id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    /**
     * Versão carregada da persistência; o adapter a usa para controle de concorrência otimista.
     * Uma instância deve ser salva no máximo uma vez por unidade de trabalho.
     */
    public long version() {
        return version;
    }

    public boolean belongsTo(OrganizationId organization) {
        return organizationId.equals(organization);
    }

    protected void registerEvent(DomainEvent event) {
        pendingEvents.add(Objects.requireNonNull(event, "event"));
    }

    /** Retorna e limpa os eventos registrados desde a última chamada. */
    public List<DomainEvent> pullEvents() {
        var events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    @Override
    public final boolean equals(Object other) {
        return other instanceof AggregateRoot<?> that
                && getClass() == that.getClass()
                && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
