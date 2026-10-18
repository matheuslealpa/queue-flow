package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Regras operacionais configuráveis por fila.
 *
 * @param ordering                    estratégia de seleção da próxima entrada
 * @param callTimeout                 tempo que uma pessoa chamada tem para se apresentar
 * @param maxCallAttempts             chamadas sem comparecimento toleradas; ao atingir o limite a entrada vira NO_SHOW
 * @param returnPosition              posição assumida ao voltar para a espera
 * @param allowReturnFromService      se uma entrada em atendimento pode ser devolvida à fila
 * @param maxConsecutivePriorityCalls intercalação: após esse número de chamadas prioritárias seguidas, a próxima
 *                                    é de uma entrada sem prioridade, se houver (p.ex. 2 = "2 para 1");
 *                                    0 mantém a prioridade estrita. Só se aplica a {@code PRIORITY_FIFO}.
 */
public record QueuePolicy(OrderingStrategy ordering,
                          Duration callTimeout,
                          int maxCallAttempts,
                          ReturnPosition returnPosition,
                          boolean allowReturnFromService,
                          int maxConsecutivePriorityCalls) {

    public static final int MAX_INTERLEAVING = 10;

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
        if (maxConsecutivePriorityCalls < 0 || maxConsecutivePriorityCalls > MAX_INTERLEAVING) {
            throw new BusinessRuleViolationException(
                    "maxConsecutivePriorityCalls deve estar entre 0 e " + MAX_INTERLEAVING);
        }
        if (maxConsecutivePriorityCalls > 0 && ordering != OrderingStrategy.PRIORITY_FIFO) {
            throw new BusinessRuleViolationException("A intercalação de prioridades exige a ordenação PRIORITY_FIFO");
        }
    }

    /** Política sem intercalação (prioridade estrita quando a ordenação for {@code PRIORITY_FIFO}). */
    public QueuePolicy(OrderingStrategy ordering, Duration callTimeout, int maxCallAttempts,
                       ReturnPosition returnPosition, boolean allowReturnFromService) {
        this(ordering, callTimeout, maxCallAttempts, returnPosition, allowReturnFromService, 0);
    }

    public static QueuePolicy defaults(OrderingStrategy ordering) {
        return new QueuePolicy(ordering, Duration.ofMinutes(3), 3, ReturnPosition.KEEP_ORIGINAL, true);
    }

    public boolean interleavesPriorities() {
        return maxConsecutivePriorityCalls > 0;
    }

    public QueuePolicy withInterleaving(int maxConsecutivePriorityCalls) {
        return new QueuePolicy(ordering, callTimeout, maxCallAttempts, returnPosition, allowReturnFromService,
                maxConsecutivePriorityCalls);
    }
}
