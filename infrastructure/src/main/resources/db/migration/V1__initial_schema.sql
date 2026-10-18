-- QueueFlow — esquema inicial.
-- Toda tabela de negócio carrega organization_id (direta ou via agregado pai) para isolamento entre tenants.
-- Instantes em TIMESTAMPTZ; identificadores UUID gerados pela aplicação; "version" para lock otimista.

CREATE TABLE organization
(
    id      UUID PRIMARY KEY,
    name    VARCHAR(150) NOT NULL,
    active  BOOLEAN      NOT NULL,
    version BIGINT       NOT NULL
);

CREATE TABLE location
(
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    name            VARCHAR(150) NOT NULL,
    time_zone       VARCHAR(64)  NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL,
    UNIQUE (organization_id, id)
);

CREATE TABLE service_offering
(
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT service_offering_code_uk UNIQUE (organization_id, code),
    UNIQUE (organization_id, id)
);

CREATE TABLE service_point
(
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    location_id     UUID         NOT NULL,
    name            VARCHAR(100) NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL,
    FOREIGN KEY (organization_id, location_id) REFERENCES location (organization_id, id),
    UNIQUE (organization_id, id)
);

CREATE TABLE queue
(
    id                        UUID PRIMARY KEY,
    organization_id           UUID         NOT NULL,
    location_id               UUID         NOT NULL,
    name                      VARCHAR(150) NOT NULL,
    ticket_prefix             VARCHAR(5)   NOT NULL,
    status                    VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'CLOSED')),
    ordering                  VARCHAR(20)  NOT NULL CHECK (ordering IN ('FIFO', 'PRIORITY_FIFO')),
    call_timeout_seconds      INTEGER      NOT NULL CHECK (call_timeout_seconds > 0),
    max_call_attempts         INTEGER      NOT NULL CHECK (max_call_attempts >= 1),
    return_position           VARCHAR(20)  NOT NULL CHECK (return_position IN ('KEEP_ORIGINAL', 'END_OF_QUEUE')),
    allow_return_from_service BOOLEAN      NOT NULL,
    version                   BIGINT       NOT NULL,
    FOREIGN KEY (organization_id, location_id) REFERENCES location (organization_id, id),
    UNIQUE (organization_id, id)
);

CREATE TABLE queue_priority_classification
(
    queue_id   UUID         NOT NULL REFERENCES queue (id) ON DELETE CASCADE,
    code       VARCHAR(40)  NOT NULL,
    label      VARCHAR(100) NOT NULL,
    level      INTEGER      NOT NULL CHECK (level BETWEEN 0 AND 100),
    restricted BOOLEAN      NOT NULL,
    position   INTEGER      NOT NULL,
    PRIMARY KEY (queue_id, code)
);

CREATE TABLE queue_accepted_service
(
    queue_id            UUID NOT NULL REFERENCES queue (id) ON DELETE CASCADE,
    service_offering_id UUID NOT NULL REFERENCES service_offering (id),
    PRIMARY KEY (queue_id, service_offering_id)
);

CREATE TABLE operator
(
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    name            VARCHAR(150) NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL
);

CREATE TABLE operator_permission
(
    operator_id UUID        NOT NULL REFERENCES operator (id) ON DELETE CASCADE,
    permission  VARCHAR(40) NOT NULL,
    PRIMARY KEY (operator_id, permission)
);

CREATE TABLE operator_assignment
(
    operator_id      UUID NOT NULL REFERENCES operator (id) ON DELETE CASCADE,
    queue_id         UUID NOT NULL REFERENCES queue (id),
    service_point_id UUID REFERENCES service_point (id),
    CONSTRAINT operator_assignment_uk UNIQUE NULLS NOT DISTINCT (operator_id, queue_id, service_point_id)
);

-- Contador de senhas por fila e dia operacional, incrementado atomicamente via UPSERT.
CREATE TABLE ticket_counter
(
    queue_id         UUID   NOT NULL REFERENCES queue (id) ON DELETE CASCADE,
    operational_date DATE   NOT NULL,
    last_value       BIGINT NOT NULL,
    PRIMARY KEY (queue_id, operational_date)
);

CREATE TABLE queue_entry
(
    id                    UUID PRIMARY KEY,
    organization_id       UUID         NOT NULL,
    queue_id              UUID         NOT NULL,
    service_offering_id   UUID REFERENCES service_offering (id),
    ticket_prefix         VARCHAR(5)   NOT NULL,
    ticket_sequence       BIGINT       NOT NULL CHECK (ticket_sequence > 0),
    operational_date      DATE         NOT NULL,
    external_reference    VARCHAR(200),
    idempotency_key       VARCHAR(100),
    priority_code         VARCHAR(40)  NOT NULL,
    priority_level        INTEGER      NOT NULL,
    status                VARCHAR(12)  NOT NULL
        CHECK (status IN ('WAITING', 'CALLED', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    created_at            TIMESTAMPTZ  NOT NULL,
    enqueued_at           TIMESTAMPTZ  NOT NULL,
    call_operator_id      UUID,
    call_service_point_id UUID,
    called_at             TIMESTAMPTZ,
    call_expires_at       TIMESTAMPTZ,
    missed_calls          INTEGER      NOT NULL CHECK (missed_calls >= 0),
    version               BIGINT       NOT NULL,
    FOREIGN KEY (organization_id, queue_id) REFERENCES queue (organization_id, id),
    CONSTRAINT queue_entry_ticket_uk UNIQUE (queue_id, operational_date, ticket_sequence),
    CONSTRAINT queue_entry_call_consistency CHECK (
        (status = 'CALLED') = (call_operator_id IS NOT NULL AND call_service_point_id IS NOT NULL
            AND called_at IS NOT NULL AND call_expires_at IS NOT NULL))
);

CREATE UNIQUE INDEX queue_entry_idempotency_uk
    ON queue_entry (organization_id, queue_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Seleção da próxima entrada: índices parciais cobrindo apenas entradas em espera.
CREATE INDEX queue_entry_waiting_priority_idx
    ON queue_entry (queue_id, priority_level DESC, enqueued_at, ticket_sequence)
    WHERE status = 'WAITING';

CREATE INDEX queue_entry_waiting_fifo_idx
    ON queue_entry (queue_id, enqueued_at, ticket_sequence)
    WHERE status = 'WAITING';

CREATE INDEX queue_entry_call_expiry_idx
    ON queue_entry (call_expires_at)
    WHERE status = 'CALLED';

CREATE INDEX queue_entry_daily_idx
    ON queue_entry (queue_id, operational_date, status);

CREATE TABLE service_session
(
    id               UUID PRIMARY KEY,
    entry_id         UUID        NOT NULL REFERENCES queue_entry (id) ON DELETE CASCADE,
    operator_id      UUID        NOT NULL REFERENCES operator (id),
    service_point_id UUID        NOT NULL REFERENCES service_point (id),
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    outcome          VARCHAR(20) CHECK (outcome IN ('COMPLETED', 'CANCELLED', 'RETURNED_TO_QUEUE')),
    CHECK ((ended_at IS NULL) = (outcome IS NULL)),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX service_session_entry_idx ON service_session (entry_id);

-- No máximo uma sessão aberta por entrada.
CREATE UNIQUE INDEX service_session_open_uk ON service_session (entry_id) WHERE ended_at IS NULL;

-- Histórico (auditoria) das operações sobre a entrada; somente inserção.
CREATE TABLE entry_history
(
    entry_id    UUID         NOT NULL REFERENCES queue_entry (id) ON DELETE CASCADE,
    seq         INTEGER      NOT NULL,
    action      VARCHAR(30)  NOT NULL,
    from_status VARCHAR(12),
    to_status   VARCHAR(12)  NOT NULL,
    actor       VARCHAR(200) NOT NULL,
    detail      VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (entry_id, seq)
);
