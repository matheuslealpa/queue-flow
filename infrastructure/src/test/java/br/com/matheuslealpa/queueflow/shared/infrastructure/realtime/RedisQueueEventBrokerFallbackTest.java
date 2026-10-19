package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisQueueEventBrokerFallbackTest {

    @Test
    void deliversLocallyWhenRedisIsUnavailable() {
        var hub = new QueueEventHub();
        var redis = new StringRedisTemplate() {
            @Override
            public Long convertAndSend(String channel, Object message) {
                throw new RedisConnectionFailureException("fora do ar");
            }
        };
        var broker = new RedisQueueEventBroker(redis, JsonMapper.builder().build(), hub);
        var org = UUID.randomUUID();
        var queue = UUID.randomUUID();
        List<QueueEvent> received = new ArrayList<>();
        hub.subscribe(new OrganizationId(org), new QueueId(queue), received::add);

        var event = new QueueEvent("entry.created", org, queue, UUID.randomUUID(), "A001", null, null, null,
                Instant.parse("2026-10-18T12:00:00Z"));
        broker.publish(event);

        assertThat(received).containsExactly(event);
    }
}
