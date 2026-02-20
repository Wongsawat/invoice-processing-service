-- Create processed_invoices table
CREATE TABLE processed_invoices (
    id UUID PRIMARY KEY,
    source_invoice_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    total_tax DECIMAL(15,2) NOT NULL,
    total DECIMAL(15,2) NOT NULL,
    original_xml TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_invoice_number ON processed_invoices(invoice_number);
CREATE INDEX idx_source_invoice_id ON processed_invoices(source_invoice_id);
CREATE INDEX idx_status ON processed_invoices(status);
CREATE INDEX idx_issue_date ON processed_invoices(issue_date);

-- Add unique constraint on invoice number
CREATE UNIQUE INDEX idx_invoice_number_unique ON processed_invoices(invoice_number);
