package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.Guards;

/**
 * Prioridade efetiva de uma entrada. Deve ser obtida por {@link Queue#resolvePriority},
 * que valida a classificação e a autorização — nunca a partir de um nível enviado pelo cliente.
 */
public record Priority(String classificationCode, int level) {

    public static final Priority NORMAL = PriorityClassification.NORMAL.toPriority();

    public Priority {
        classificationCode = Guards.notBlank(classificationCode, "classificationCode");
    }
}
