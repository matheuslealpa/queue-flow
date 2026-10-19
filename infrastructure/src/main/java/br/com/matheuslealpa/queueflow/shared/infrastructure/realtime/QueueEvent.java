package br.com.matheuslealpa.queueflow.shared.infrastructure.realtime;

import java.time.Instant;
import java.util.UUID;

/**
 * Notificação de que algo mudou em uma fila, enviada aos clientes em tempo real (painel, totem,
 * guichês). É deliberadamente enxuta: o cliente recarrega o que precisa pela API REST; só a
 * chamada carrega senha e destino, para o painel anunciá-la sem outra consulta.
 *
 * @param type            p.ex. {@code entry.called}, {@code queue.status-changed}
 * @param ticket          senha, quando o evento a conhece (criação e chamada)
 * @param status          novo status da fila em {@code queue.status-changed}
 * @param servicePointName nome do guichê em {@code entry.called}
 */
public record QueueEvent(String type,
                         UUID organizationId,
                         UUID queueId,
                         UUID entryId,
                         String ticket,
                         String status,
                         UUID servicePointId,
                         String servicePointName,
                         Instant occurredAt) {

    /** Canal Redis da fila; o tenant faz parte da chave para não misturar organizações. */
    public String channel() {
        return QueueEventChannels.of(organizationId, queueId);
    }
}
