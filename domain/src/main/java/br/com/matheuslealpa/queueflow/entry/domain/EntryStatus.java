package br.com.matheuslealpa.queueflow.entry.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de uma entrada e as transições permitidas entre eles.
 */
public enum EntryStatus {
    WAITING,
    CALLED,
    IN_SERVICE,
    COMPLETED,
    CANCELLED,
    /** Encerrada por exceder o número de chamadas sem comparecimento. */
    NO_SHOW;

    private Set<EntryStatus> allowedTargets() {
        return switch (this) {
            case WAITING -> EnumSet.of(CALLED, CANCELLED);
            case CALLED -> EnumSet.of(IN_SERVICE, WAITING, NO_SHOW, CANCELLED);
            case IN_SERVICE -> EnumSet.of(COMPLETED, CANCELLED, WAITING);
            case COMPLETED, CANCELLED, NO_SHOW -> EnumSet.noneOf(EntryStatus.class);
        };
    }

    public boolean canTransitionTo(EntryStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }
}
