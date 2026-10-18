package br.com.matheuslealpa.queueflow.queue.domain;

public enum QueueStatus {
    /** Recebe novas entradas e permite chamadas. */
    ACTIVE,
    /** Não recebe novas entradas, mas as já existentes continuam sendo chamadas e atendidas. */
    PAUSED,
    /** Encerrada definitivamente: sem novas entradas nem chamadas. */
    CLOSED;

    public boolean acceptsEntries() {
        return this == ACTIVE;
    }

    public boolean allowsCalls() {
        return this != CLOSED;
    }
}
