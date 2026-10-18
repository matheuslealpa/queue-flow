package br.com.matheuslealpa.queueflow.queue.domain;

/**
 * Faixa de prioridade considerada em uma seleção de entradas.
 */
public enum PriorityBand {
    /** Qualquer entrada. */
    ANY,
    /** Apenas entradas com prioridade (nível maior que zero). */
    PRIORITIZED,
    /** Apenas entradas sem prioridade (nível zero). */
    REGULAR;

    public boolean includes(int priorityLevel) {
        return switch (this) {
            case ANY -> true;
            case PRIORITIZED -> priorityLevel > 0;
            case REGULAR -> priorityLevel == 0;
        };
    }
}
