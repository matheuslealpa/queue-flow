package br.com.matheuslealpa.queueflow.entry.domain;

import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Senha exibida para a pessoa. Única por fila e dia operacional, não globalmente.
 */
public record TicketNumber(String prefix, long sequence, LocalDate operationalDate) {

    public TicketNumber {
        prefix = Guards.notBlank(prefix, "prefix");
        Objects.requireNonNull(operationalDate, "operationalDate");
        if (sequence < 1) {
            throw new BusinessRuleViolationException("A sequência da senha deve ser positiva");
        }
    }

    /** Representação para painéis e comprovantes, p.ex. {@code A007}. */
    public String display() {
        return "%s%03d".formatted(prefix, sequence);
    }

    @Override
    public String toString() {
        return display();
    }
}
