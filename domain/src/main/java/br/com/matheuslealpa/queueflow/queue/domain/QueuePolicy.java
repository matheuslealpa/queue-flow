package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Regras operacionais configuráveis por fila.
 *
 * @param ordering               estratégia de seleção da próxima entrada
 * @param callTimeout            tempo que uma pessoa chamada tem para se apresentar
 * @param maxCallAttempts        chamadas sem comparecimento toleradas; ao atingir o limite a entrada vira NO_SHOW
 * @param returnPosition         posição assumida ao voltar para a espera
 * @param allowReturnFromService se uma entrada em atendimento pode ser devolvida à fila
 */
public record QueuePolicy(OrderingStrategy ordering,
                          Duration callTimeout,
                          int maxCallAttempts,
                          ReturnPosition returnPosition,
                          boolean allowReturnFromService) {

    public QueuePolicy {
        Objects.requireNonNull(ordering, "ordering");
        Objects.requireNonNull(callTimeout, "callTimeout");
        Objects.requireNonNull(returnPosition, "returnPosition");
        if (callTimeout.isNegative() || callTimeout.isZero()) {
            throw new BusinessRuleViolationException("callTimeout deve ser positivo");
        }
        if (maxCallAttempts < 1) {
            throw new BusinessRuleViolationException("maxCallAttempts deve ser ao menos 1");
        }
    }

    public static QueuePolicy defaults(OrderingStrategy ordering) {
        return new QueuePolicy(ordering, Duration.ofMinutes(3), 3, ReturnPosition.KEEP_ORIGINAL, true);
    }
}
