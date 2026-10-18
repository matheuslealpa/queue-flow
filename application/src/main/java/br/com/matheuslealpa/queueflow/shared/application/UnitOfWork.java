package br.com.matheuslealpa.queueflow.shared.application;

import java.util.function.Supplier;

/**
 * Fronteira transacional de um caso de uso. A infraestrutura a implementa (p.ex. com
 * {@code TransactionTemplate}); tudo executado dentro de {@link #execute} é confirmado ou
 * desfeito em conjunto.
 */
@FunctionalInterface
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);
}
