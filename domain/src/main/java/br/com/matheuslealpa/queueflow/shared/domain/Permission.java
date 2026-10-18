package br.com.matheuslealpa.queueflow.shared.domain;

/**
 * Capacidades da plataforma concedidas a operadores. Não representam regras de
 * negócio por organização (essas são configuradas como dados, p.ex. classificações de prioridade).
 */
public enum Permission {
    CALL_ENTRIES,
    ASSIGN_RESTRICTED_PRIORITY,
    CHANGE_PRIORITY,
    CANCEL_ENTRY,
    MANAGE_QUEUES,
    MANAGE_ORGANIZATION
}
