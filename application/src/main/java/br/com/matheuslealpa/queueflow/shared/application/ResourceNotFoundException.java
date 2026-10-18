package br.com.matheuslealpa.queueflow.shared.application;

/**
 * O recurso não existe ou pertence a outra organização (os dois casos são indistinguíveis
 * para o solicitante, preservando o isolamento entre tenants).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s %s não encontrado".formatted(resource, id));
    }
}
