-- Intercalação de prioridades: limite configurável e contador de chamadas prioritárias seguidas.
ALTER TABLE queue
    ADD COLUMN max_consecutive_priority_calls INTEGER NOT NULL DEFAULT 0
        CHECK (max_consecutive_priority_calls BETWEEN 0 AND 10),
    ADD COLUMN consecutive_priority_calls INTEGER NOT NULL DEFAULT 0
        CHECK (consecutive_priority_calls >= 0),
    ADD CONSTRAINT queue_interleaving_requires_priority
        CHECK (max_consecutive_priority_calls = 0 OR ordering = 'PRIORITY_FIFO');

-- Seleção da próxima entrada sem prioridade quando a intercalação exige.
CREATE INDEX queue_entry_waiting_regular_idx
    ON queue_entry (queue_id, enqueued_at, ticket_sequence)
    WHERE status = 'WAITING' AND priority_level = 0;
