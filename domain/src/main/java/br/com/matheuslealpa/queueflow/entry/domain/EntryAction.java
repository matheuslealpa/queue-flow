package br.com.matheuslealpa.queueflow.entry.domain;

public enum EntryAction {
    CREATED,
    CALLED,
    CALL_EXPIRED,
    NOT_PRESENT,
    SERVICE_STARTED,
    COMPLETED,
    CANCELLED,
    RETURNED_TO_QUEUE,
    PRIORITY_CHANGED
}
