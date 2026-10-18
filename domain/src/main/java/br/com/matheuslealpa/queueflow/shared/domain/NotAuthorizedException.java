package br.com.matheuslealpa.queueflow.shared.domain;

public class NotAuthorizedException extends DomainException {

    public NotAuthorizedException(Actor actor, String action) {
        super("%s não está autorizado a %s".formatted(actor.describe(), action));
    }
}
