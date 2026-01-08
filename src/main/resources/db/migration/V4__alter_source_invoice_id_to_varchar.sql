-- Alter source_invoice_id column from UUID to VARCHAR
ALTER TABLE processed_invoices
    ALTER COLUMN source_invoice_id TYPE VARCHAR(100)
    USING source_invoice_id::text;
