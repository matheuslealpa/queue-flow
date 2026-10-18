package br.com.matheuslealpa.queueflow.shared.domain;

import java.util.Objects;

/**
 * Validações de invariantes reutilizadas pelos objetos de domínio.
 */
public final class Guards {

    private Guards() {
    }

    public static String notBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(field + " é obrigatório");
        }
        return value.strip();
    }

    public static String maxLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new BusinessRuleViolationException("%s excede %d caracteres".formatted(field, max));
        }
        return value;
    }

    public static <T> T required(T value, String field) {
        return Objects.requireNonNull(value, field + " é obrigatório");
    }
}
