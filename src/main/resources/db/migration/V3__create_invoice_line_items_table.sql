-- Create invoice_line_items table
CREATE TABLE invoice_line_items (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    tax_rate DECIMAL(5,2) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    tax_amount DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_invoice_line_items_invoice FOREIGN KEY (invoice_id)
        REFERENCES processed_invoices(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_line_item_invoice ON invoice_line_items(invoice_id);

-- Add unique constraint on invoice_id + line_number
CREATE UNIQUE INDEX idx_invoice_line_number ON invoice_line_items(invoice_id, line_number);
