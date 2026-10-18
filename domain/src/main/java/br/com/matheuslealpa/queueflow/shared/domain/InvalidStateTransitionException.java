package br.com.matheuslealpa.queueflow.shared.domain;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(Enum<?> from, Enum<?> to, String action) {
        super("Transição inválida %s -> %s ao executar '%s'".formatted(from, to, action));
    }
}
