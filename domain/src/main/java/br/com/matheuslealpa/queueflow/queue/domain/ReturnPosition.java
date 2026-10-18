package br.com.matheuslealpa.queueflow.queue.domain;

/**
 * Posição que uma entrada assume ao voltar para a espera (não comparecimento ou devolução).
 */
public enum ReturnPosition {
    /** Mantém o instante original de chegada, retomando sua posição relativa. */
    KEEP_ORIGINAL,
    /** Passa a contar a chegada a partir do retorno, indo para o fim do seu nível de prioridade. */
    END_OF_QUEUE
}
