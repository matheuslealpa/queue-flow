package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import br.com.matheuslealpa.queueflow.InfrastructureTestApplication;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Ida e volta pelo Redis Pub/Sub real: o que uma instância publica chega aos assinantes de todas. */
@SpringBootTest(classes = InfrastructureTestApplication.class, properties = "queueflow.events.broker=redis")
@Import({TestcontainersConfiguration.class, RedisQueueEventBrokerIntegrationTest.Redis.class})
class RedisQueueEventBrokerIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Redis {
        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
        }
    }

    @Autowired
    QueueEventBroker broker;

    @Autowired
    QueueEventHub hub;

    @Test
    void deliversThroughRedisOnlyToTheQueueOfTheSameOrganization() throws InterruptedException {
        assertThat(broker).isInstanceOf(RedisQueueEventBroker.class);
        var org = UUID.randomUUID();
        var queue = UUID.randomUUID();
        BlockingQueue<QueueEvent> received = new LinkedBlockingQueue<>();
        BlockingQueue<QueueEvent> otherTenant = new LinkedBlockingQueue<>();

        try (var _ = hub.subscribe(new OrganizationId(org), new QueueId(queue), received::add);
             var _ = hub.subscribe(new OrganizationId(UUID.randomUUID()), new QueueId(queue), otherTenant::add)) {
            var event = new QueueEvent("entry.called", org, queue, UUID.randomUUID(), "A001", null,
                    UUID.randomUUID(), "Guichê 1", Instant.parse("2026-10-18T12:00:00Z"));
            // a assinatura do container é assíncrona: republica até ela estar ativa
            QueueEvent delivered = null;
            for (var attempt = 0; attempt < 50 && delivered == null; attempt++) {
                broker.publish(event);
                delivered = received.poll(100, TimeUnit.MILLISECONDS);
            }

            assertThat(delivered).isEqualTo(event);
        }
        assertThat(otherTenant).isEmpty();
    }
}
