package br.com.matheuslealpa.queueflow.queue.domain;

import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;

import java.util.Locale;

/**
 * Classificação de prioridade configurada para uma fila (p.ex. "PREFERENCIAL", "URGENTE").
 * As regras de quem tem direito a cada classificação pertencem a cada organização; o núcleo
 * só garante o nível, a identificação e a exigência de autorização.
 *
 * @param restricted exige {@code ASSIGN_RESTRICTED_PRIORITY} para ser atribuída
 */
public record PriorityClassification(String code, String label, int level, boolean restricted) {

    public static final String NORMAL_CODE = "NORMAL";
    public static final int MAX_LEVEL = 100;
    public static final PriorityClassification NORMAL = new PriorityClassification(NORMAL_CODE, "Normal", 0, false);

    public PriorityClassification {
        code = normalizeCode(code);
        label = Guards.maxLength(Guards.notBlank(label, "label"), 100, "label");
        if (level < 0 || level > MAX_LEVEL) {
            throw new BusinessRuleViolationException("level deve estar entre 0 e " + MAX_LEVEL);
        }
        if (NORMAL_CODE.equals(code) && (level != 0 || restricted)) {
            throw new BusinessRuleViolationException("A classificação NORMAL deve ter nível 0 e não ser restrita");
        }
    }

    public static String normalizeCode(String code) {
        return Guards.maxLength(Guards.notBlank(code, "code"), 40, "code").toUpperCase(Locale.ROOT);
    }

    public Priority toPriority() {
        return new Priority(code, level);
    }
}
