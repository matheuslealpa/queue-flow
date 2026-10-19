package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eventos de fila em tempo real. {@code queueflow.events.broker} escolhe a distribuição:
 * {@code redis} (padrão, várias instâncias) ou {@code local} (memória, instância única).
 */
@Configuration(proxyBeanMethods = false)
public class RealtimeConfiguration {

    @Bean
    QueueEventHub queueEventHub() {
        return new QueueEventHub();
    }

    @Bean
    QueueEventRelay queueEventRelay(ServicePointRepository servicePoints, QueueEventBroker broker) {
        return new QueueEventRelay(new QueueEventMapper(servicePoints), broker);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "queueflow.events.broker", havingValue = "redis", matchIfMissing = true)
    static class Redis {

        @Bean
        RedisQueueEventBroker redisQueueEventBroker(StringRedisTemplate redis, JsonMapper json, QueueEventHub hub) {
            return new RedisQueueEventBroker(redis, json, hub);
        }

        @Bean
        RedisMessageListenerContainer queueEventListenerContainer(RedisConnectionFactory connections,
                                                                  RedisQueueEventBroker broker) {
            var container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connections);
            container.addMessageListener(broker, new PatternTopic(QueueEventChannels.PATTERN));
            return container;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "queueflow.events.broker", havingValue = "local")
    static class Local {

        @Bean
        QueueEventBroker localQueueEventBroker(QueueEventHub hub) {
            return QueueEventBroker.local(hub);
        }
    }
}
