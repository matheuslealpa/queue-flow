package br.com.matheuslealpa.queueflow.entry.domain;

public enum MissedCallReason {
    /** O prazo de comparecimento terminou sem início do atendimento. */
    CALL_EXPIRED,
    /** O operador informou que a pessoa não se apresentou. */
    NOT_PRESENT
}
