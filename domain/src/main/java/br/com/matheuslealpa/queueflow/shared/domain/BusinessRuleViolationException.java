package br.com.matheuslealpa.queueflow.shared.domain;

public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
