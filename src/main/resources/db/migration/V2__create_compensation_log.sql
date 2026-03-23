-- V2__create_compensation_log.sql
-- Permanent audit trail for saga compensation events.
-- Rows are NEVER deleted — this table is the forensic record of what was deleted and why.
CREATE TABLE compensation_log (
    id               UUID         PRIMARY KEY,
    source_invoice_id VARCHAR(100) NOT NULL,
    invoice_id       UUID,                        -- NULL when reason = ALREADY_ABSENT
    invoice_number   VARCHAR(50),                 -- NULL when reason = ALREADY_ABSENT
    saga_id          VARCHAR(255) NOT NULL,
    correlation_id   VARCHAR(255),
    reason           VARCHAR(20)  NOT NULL,        -- COMPENSATED | ALREADY_ABSENT
    compensated_at   TIMESTAMP WITH TIME ZONE NOT NULL  -- Instant -> TIMESTAMPTZ (Hibernate 6 default)
);

CREATE INDEX idx_compensation_source_invoice ON compensation_log(source_invoice_id);
CREATE INDEX idx_compensation_saga          ON compensation_log(saga_id);
CREATE INDEX idx_compensation_at            ON compensation_log(compensated_at);

COMMENT ON TABLE compensation_log IS
    'Permanent audit trail of saga compensation events. Never truncated or cleaned up.';
COMMENT ON COLUMN compensation_log.invoice_id IS
    'UUID of the ProcessedInvoice that was deleted. NULL if the row was already absent.';
COMMENT ON COLUMN compensation_log.reason IS
    'COMPENSATED = row was found and deleted; ALREADY_ABSENT = row was not found.';
