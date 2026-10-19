package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica cada evento no canal da fila e, como ouvinte do padrão de canais, repassa ao
 * {@link QueueEventHub} local tudo o que qualquer instância publicou (inclusive esta).
 *
 * <p>Pub/Sub não guarda mensagens: quem estiver desconectado perde o evento. Os clientes
 * recarregam o estado ao reconectar, então isso é aceitável.
 *
 * <p>Com o Redis fora do ar, entrega ao menos aos clientes desta instância: as conexões SSE
 * continuam abertas e, sem isso, as telas ficariam paradas achando que estão em tempo real.
 */
public class RedisQueueEventBroker implements QueueEventBroker, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueEventBroker.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final QueueEventHub hub;

    public RedisQueueEventBroker(StringRedisTemplate redis, JsonMapper json, QueueEventHub hub) {
        this.redis = redis;
        this.json = json;
        this.hub = hub;
    }

    @Override
    public void publish(QueueEvent event) {
        try {
            redis.convertAndSend(event.channel(), json.writeValueAsString(event));
        } catch (RuntimeException e) {
            log.warn("Redis indisponível; {} entregue só aos clientes desta instância", event.type(), e);
            hub.dispatch(event);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            hub.dispatch(json.readValue(message.getBody(), QueueEvent.class));
        } catch (RuntimeException e) {
            log.warn("Mensagem inválida no canal {}", new String(message.getChannel()), e);
        }
    }
}
