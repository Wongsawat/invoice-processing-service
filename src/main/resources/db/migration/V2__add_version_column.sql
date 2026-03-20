-- Add optimistic-locking version column to processed_invoices.
-- Default 0 so existing rows are valid without a data migration.
ALTER TABLE processed_invoices
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
