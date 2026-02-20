-- Create tax_invoice_parties table
CREATE TABLE tax_invoice_parties (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    party_type VARCHAR(10) NOT NULL,
    name VARCHAR(200) NOT NULL,
    tax_id VARCHAR(50),
    tax_id_scheme VARCHAR(20),
    street_address VARCHAR(500),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    CONSTRAINT fk_tax_invoice_parties_invoice FOREIGN KEY (invoice_id)
        REFERENCES processed_tax_invoices(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_tax_party_invoice ON tax_invoice_parties(invoice_id);
CREATE INDEX idx_tax_party_type ON tax_invoice_parties(party_type);
