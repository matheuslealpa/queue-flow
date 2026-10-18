package br.com.matheuslealpa.queueflow.entry.api;

import br.com.matheuslealpa.queueflow.entry.application.ExpireCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expira periodicamente chamadas sem comparecimento. Seguro com várias instâncias: cada entrada é
 * processada em transação própria com lock otimista.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "queueflow.calls.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class CallExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CallExpirationScheduler.class);

    private final ExpireCalls expireCalls;
    private final int batchSize;

    public CallExpirationScheduler(ExpireCalls expireCalls,
                                   @Value("${queueflow.calls.expiration.batch-size:200}") int batchSize) {
        this.expireCalls = expireCalls;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${queueflow.calls.expiration.interval:PT15S}")
    void expire() {
        var expired = expireCalls.handle(batchSize);
        if (expired > 0) {
            log.info("{} chamada(s) expirada(s)", expired);
        }
    }
}
