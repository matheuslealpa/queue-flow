package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.shared.application.UnitOfWork;

import java.util.function.Supplier;

/** Unidade de trabalho que libera, ao final, os bloqueios de entradas obtidos pela thread. */
public final class InMemoryUnitOfWork implements UnitOfWork {

    private final InMemoryQueueEntryRepository entries;

    public InMemoryUnitOfWork(InMemoryQueueEntryRepository entries) {
        this.entries = entries;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        try {
            return work.get();
        } finally {
            entries.releaseLocksOf(Thread.currentThread());
        }
    }
}
