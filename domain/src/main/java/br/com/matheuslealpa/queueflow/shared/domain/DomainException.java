package br.com.matheuslealpa.queueflow.shared.domain;

/**
 * Base de todas as violações de regras de negócio detectadas pelo domínio.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
