package br.com.matheuslealpa.queueflow.entry.infrastructure;

import br.com.matheuslealpa.queueflow.entry.domain.CurrentCall;
import br.com.matheuslealpa.queueflow.entry.domain.DuplicateIdempotencyKeyException;
import br.com.matheuslealpa.queueflow.entry.domain.EntryAction;
import br.com.matheuslealpa.queueflow.entry.domain.EntryHistoryRecord;
import br.com.matheuslealpa.queueflow.entry.domain.EntryStatus;
import br.com.matheuslealpa.queueflow.entry.domain.ExternalReference;
import br.com.matheuslealpa.queueflow.entry.domain.IdempotencyKey;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntry;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryId;
import br.com.matheuslealpa.queueflow.entry.domain.QueueEntryRepository;
import br.com.matheuslealpa.queueflow.entry.domain.ServiceSession;
import br.com.matheuslealpa.queueflow.entry.domain.ServiceSessionId;
import br.com.matheuslealpa.queueflow.entry.domain.SessionOutcome;
import br.com.matheuslealpa.queueflow.entry.domain.TicketNumber;
import br.com.matheuslealpa.queueflow.queue.domain.EntrySelection;
import br.com.matheuslealpa.queueflow.queue.domain.Priority;
import br.com.matheuslealpa.queueflow.queue.domain.QueueId;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import br.com.matheuslealpa.queueflow.shared.infrastructure.Jdbc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistência de {@link QueueEntry}. A linha principal guarda o estado corrente; sessões e
 * histórico ficam em tabelas filhas, carregadas em lote para evitar N+1.
 */
@Repository
public class JdbcQueueEntryRepository implements QueueEntryRepository {

    private static final String IDEMPOTENCY_CONSTRAINT = "queue_entry_idempotency_uk";

    private static final String COLUMNS = """
            id, organization_id, queue_id, service_offering_id, ticket_prefix, ticket_sequence, operational_date,
            external_reference, idempotency_key, priority_code, priority_level, status, created_at, enqueued_at,
            call_operator_id, call_service_point_id, called_at, call_expires_at, missed_calls, version
            """;

    private final JdbcClient jdbc;

    public JdbcQueueEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- consultas ----------------------------------------------------------------------

    @Override
    public Optional<QueueEntry> findById(OrganizationId organizationId, QueueEntryId entryId) {
        return single(jdbc.sql("SELECT " + COLUMNS + " FROM queue_entry WHERE organization_id = :org AND id = :id")
                .param("org", organizationId.value())
                .param("id", entryId.value())
                .query(ROW)
                .list());
    }

    @Override
    public Optional<QueueEntry> findByIdempotencyKey(OrganizationId organizationId, QueueId queueId,
                                                     IdempotencyKey key) {
        return single(jdbc.sql("""
                        SELECT %s FROM queue_entry
                        WHERE organization_id = :org AND queue_id = :queue AND idempotency_key = :key
                        """.formatted(COLUMNS))
                .param("org", organizationId.value())
                .param("queue", queueId.value())
                .param("key", key.value())
                .query(ROW)
                .list());
    }

    /**
     * Bloqueia apenas a primeira linha elegível. {@code SKIP LOCKED} faz transações concorrentes
     * pularem a linha já reservada e seguirem para a próxima, sem espera nem chamada duplicada.
     */
    @Override
    public Optional<QueueEntry> lockNextWaiting(OrganizationId organizationId, QueueId queueId,
                                                EntrySelection selection) {
        return single(jdbc.sql("""
                        SELECT %s FROM queue_entry
                        WHERE organization_id = :org AND queue_id = :queue AND status = 'WAITING' %s
                        %s
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """.formatted(COLUMNS, EntryOrderingSql.bandFilter(selection.band()),
                        EntryOrderingSql.orderBy(selection.ordering())))
                .param("org", organizationId.value())
                .param("queue", queueId.value())
                .query(ROW)
                .list());
    }

    @Override
    public List<QueueEntry> findWaiting(OrganizationId organizationId, QueueId queueId, EntrySelection selection,
                                        int offset, int limit) {
        return restore(jdbc.sql("""
                        SELECT %s FROM queue_entry
                        WHERE organization_id = :org AND queue_id = :queue AND status = 'WAITING' %s
                        %s
                        OFFSET :offset LIMIT :limit
                        """.formatted(COLUMNS, EntryOrderingSql.bandFilter(selection.band()),
                        EntryOrderingSql.orderBy(selection.ordering())))
                .param("org", organizationId.value())
                .param("queue", queueId.value())
                .param("offset", offset)
                .param("limit", limit)
                .query(ROW)
                .list());
    }

    @Override
    public List<QueueEntry> findExpiredCalls(Instant now, int limit) {
        return restore(jdbc.sql("""
                        SELECT %s FROM queue_entry
                        WHERE status = 'CALLED' AND call_expires_at <= :now
                        ORDER BY call_expires_at
                        LIMIT :limit
                        """.formatted(COLUMNS))
                .param("now", Jdbc.timestamp(now))
                .param("limit", limit)
                .query(ROW)
                .list());
    }

    // ---- escrita ------------------------------------------------------------------------

    @Override
    public void save(QueueEntry entry) {
        try {
            upsertEntry(entry);
        } catch (DuplicateKeyException e) {
            if (e.getMessage() != null && e.getMessage().contains(IDEMPOTENCY_CONSTRAINT)) {
                throw new DuplicateIdempotencyKeyException(entry.queueId(), entry.idempotencyKey().orElseThrow());
            }
            throw e;
        }
        for (var session : entry.sessions()) {
            upsertSession(entry.id(), session);
        }
        var history = entry.history();
        for (int seq = 0; seq < history.size(); seq++) {
            insertHistory(entry.id(), seq, history.get(seq));
        }
    }

    private void upsertEntry(QueueEntry entry) {
        var call = entry.currentCall();
        var rows = jdbc.sql("""
                        INSERT INTO queue_entry (%s)
                        VALUES (:id, :org, :queue, :service, :prefix, :sequence, :operationalDate, :externalReference,
                                :idempotencyKey, :priorityCode, :priorityLevel, :status, :createdAt, :enqueuedAt,
                                :callOperator, :callServicePoint, :calledAt, :callExpiresAt, :missedCalls, :version)
                        ON CONFLICT (id) DO UPDATE
                            SET priority_code = excluded.priority_code,
                                priority_level = excluded.priority_level,
                                status = excluded.status,
                                enqueued_at = excluded.enqueued_at,
                                call_operator_id = excluded.call_operator_id,
                                call_service_point_id = excluded.call_service_point_id,
                                called_at = excluded.called_at,
                                call_expires_at = excluded.call_expires_at,
                                missed_calls = excluded.missed_calls,
                                version = queue_entry.version + 1
                            WHERE queue_entry.version = excluded.version
                              AND queue_entry.organization_id = excluded.organization_id
                        """.formatted(COLUMNS))
                .param("id", entry.id().value())
                .param("org", entry.organizationId().value())
                .param("queue", entry.queueId().value())
                .param("service", entry.serviceOfferingId().map(ServiceOfferingId::value).orElse(null))
                .param("prefix", entry.ticket().prefix())
                .param("sequence", entry.ticket().sequence())
                .param("operationalDate", entry.ticket().operationalDate())
                .param("externalReference", entry.externalReference().map(ExternalReference::value).orElse(null))
                .param("idempotencyKey", entry.idempotencyKey().map(IdempotencyKey::value).orElse(null))
                .param("priorityCode", entry.priority().classificationCode())
                .param("priorityLevel", entry.priority().level())
                .param("status", entry.status().name())
                .param("createdAt", Jdbc.timestamp(entry.createdAt()))
                .param("enqueuedAt", Jdbc.timestamp(entry.enqueuedAt()))
                .param("callOperator", call.map(c -> c.operatorId().value()).orElse(null))
                .param("callServicePoint", call.map(c -> c.servicePointId().value()).orElse(null))
                .param("calledAt", Jdbc.timestamp(call.map(CurrentCall::calledAt).orElse(null)))
                .param("callExpiresAt", Jdbc.timestamp(call.map(CurrentCall::expiresAt).orElse(null)))
                .param("missedCalls", entry.missedCalls())
                .param("version", entry.version())
                .update();
        Jdbc.requireWritten(rows, "Entrada", entry.id());
    }

    private void upsertSession(QueueEntryId entryId, ServiceSession session) {
        jdbc.sql("""
                        INSERT INTO service_session (id, entry_id, operator_id, service_point_id, started_at, ended_at, outcome)
                        VALUES (:id, :entry, :operator, :servicePoint, :startedAt, :endedAt, :outcome)
                        ON CONFLICT (id) DO UPDATE SET ended_at = excluded.ended_at, outcome = excluded.outcome
                        """)
                .param("id", session.id().value())
                .param("entry", entryId.value())
                .param("operator", session.operatorId().value())
                .param("servicePoint", session.servicePointId().value())
                .param("startedAt", Jdbc.timestamp(session.startedAt()))
                .param("endedAt", Jdbc.timestamp(session.endedAt().orElse(null)))
                .param("outcome", session.outcome().map(Enum::name).orElse(null))
                .update();
    }

    private void insertHistory(QueueEntryId entryId, int seq, EntryHistoryRecord record) {
        jdbc.sql("""
                        INSERT INTO entry_history (entry_id, seq, action, from_status, to_status, actor, detail, occurred_at)
                        VALUES (:entry, :seq, :action, :from, :to, :actor, :detail, :occurredAt)
                        ON CONFLICT (entry_id, seq) DO NOTHING
                        """)
                .param("entry", entryId.value())
                .param("seq", seq)
                .param("action", record.action().name())
                .param("from", record.from() == null ? null : record.from().name())
                .param("to", record.to().name())
                .param("actor", record.actor())
                .param("detail", record.detail())
                .param("occurredAt", Jdbc.timestamp(record.occurredAt()))
                .update();
    }

    // ---- reconstituição -----------------------------------------------------------------

    /** Linha principal; sessões e histórico são acrescentados em {@link #restore(List)}. */
    private record Row(QueueEntry.Snapshot snapshot) {
    }

    private static final RowMapper<Row> ROW = (rs, n) -> {
        var callOperator = Jdbc.uuid(rs, "call_operator_id");
        var call = callOperator == null ? null : new CurrentCall(new OperatorId(callOperator),
                new ServicePointId(Jdbc.uuid(rs, "call_service_point_id")), Jdbc.instant(rs, "called_at"),
                Jdbc.instant(rs, "call_expires_at"));
        var service = Jdbc.uuid(rs, "service_offering_id");
        var externalReference = rs.getString("external_reference");
        var idempotencyKey = rs.getString("idempotency_key");
        return new Row(new QueueEntry.Snapshot(
                new QueueEntryId(Jdbc.uuid(rs, "id")),
                new OrganizationId(Jdbc.uuid(rs, "organization_id")),
                new QueueId(Jdbc.uuid(rs, "queue_id")),
                service == null ? null : new ServiceOfferingId(service),
                new TicketNumber(rs.getString("ticket_prefix"), rs.getLong("ticket_sequence"),
                        rs.getObject("operational_date", LocalDate.class)),
                externalReference == null ? null : new ExternalReference(externalReference),
                idempotencyKey == null ? null : new IdempotencyKey(idempotencyKey),
                new Priority(rs.getString("priority_code"), rs.getInt("priority_level")),
                EntryStatus.valueOf(rs.getString("status")),
                Jdbc.instant(rs, "created_at"),
                Jdbc.instant(rs, "enqueued_at"),
                call,
                rs.getInt("missed_calls"),
                List.of(),
                List.of(),
                rs.getLong("version")));
    };

    private Optional<QueueEntry> single(List<Row> rows) {
        return restore(rows).stream().findFirst();
    }

    private List<QueueEntry> restore(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        var ids = rows.stream().map(r -> r.snapshot().id().value()).toList();
        Map<UUID, List<ServiceSession>> sessions = jdbc.sql("""
                        SELECT id, entry_id, operator_id, service_point_id, started_at, ended_at, outcome
                        FROM service_session WHERE entry_id IN (:ids) ORDER BY started_at
                        """)
                .param("ids", ids)
                .query((rs, n) -> Map.entry(Jdbc.uuid(rs, "entry_id"), session(rs)))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<UUID, List<EntryHistoryRecord>> history = jdbc.sql("""
                        SELECT entry_id, action, from_status, to_status, actor, detail, occurred_at
                        FROM entry_history WHERE entry_id IN (:ids) ORDER BY entry_id, seq
                        """)
                .param("ids", ids)
                .query((rs, n) -> Map.entry(Jdbc.uuid(rs, "entry_id"), historyRecord(rs)))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        var entries = new ArrayList<QueueEntry>(rows.size());
        for (var row : rows) {
            var s = row.snapshot();
            var id = s.id().value();
            entries.add(QueueEntry.restore(new QueueEntry.Snapshot(s.id(), s.organizationId(), s.queueId(),
                    s.serviceOfferingId(), s.ticket(), s.externalReference(), s.idempotencyKey(), s.priority(),
                    s.status(), s.createdAt(), s.enqueuedAt(), s.currentCall(), s.missedCalls(),
                    sessions.getOrDefault(id, List.of()), history.getOrDefault(id, List.of()), s.version())));
        }
        return entries;
    }

    private static ServiceSession session(ResultSet rs) throws SQLException {
        var outcome = rs.getString("outcome");
        return ServiceSession.restore(new ServiceSessionId(Jdbc.uuid(rs, "id")),
                new OperatorId(Jdbc.uuid(rs, "operator_id")), new ServicePointId(Jdbc.uuid(rs, "service_point_id")),
                Jdbc.instant(rs, "started_at"), Jdbc.instant(rs, "ended_at"),
                outcome == null ? null : SessionOutcome.valueOf(outcome));
    }

    private static EntryHistoryRecord historyRecord(ResultSet rs) throws SQLException {
        var from = rs.getString("from_status");
        return new EntryHistoryRecord(EntryAction.valueOf(rs.getString("action")),
                from == null ? null : EntryStatus.valueOf(from), EntryStatus.valueOf(rs.getString("to_status")),
                rs.getString("actor"), rs.getString("detail"), Jdbc.instant(rs, "occurred_at"));
    }
}
