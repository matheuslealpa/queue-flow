package br.com.matheuslealpa.queueflow.shared.infrastructure;

import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Conversões e verificações comuns aos adapters JDBC.
 */
public final class Jdbc {

    private Jdbc() {
    }

    public static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    /**
     * Valida o resultado de um UPSERT condicionado à versão: zero linhas afetadas significa que
     * outra transação alterou o agregado depois que ele foi carregado.
     */
    public static void requireWritten(int rows, String aggregate, Object id) {
        if (rows != 1) {
            throw new OptimisticLockingFailureException(
                    "%s %s foi alterado por outra operação; recarregue e tente novamente".formatted(aggregate, id));
        }
    }
}
