package br.com.matheuslealpa.queueflow.shared.api;

/** A requisição não identifica a organização ou o solicitante. */
public class MissingIdentityException extends RuntimeException {

    public MissingIdentityException(String message) {
        super(message);
    }
}
