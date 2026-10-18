package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositório em memória que, como o adapter real, filtra toda busca pela organização.
 */
public abstract class InMemoryRepository<T extends AggregateRoot<ID>, ID> {

    protected final Map<ID, T> store = new ConcurrentHashMap<>();

    public Optional<T> findById(OrganizationId organizationId, ID id) {
        return Optional.ofNullable(store.get(id)).filter(aggregate -> aggregate.belongsTo(organizationId));
    }

    public void save(T aggregate) {
        store.put(aggregate.id(), aggregate);
    }

    public int count() {
        return store.size();
    }
}
