package com.wpanther.taxinvoice.processing.domain.service;

import com.wpanther.taxinvoice.processing.domain.model.ProcessedTaxInvoice;

/**
 * Domain service for parsing XML tax invoices
 */
public interface TaxInvoiceParserService {

    /**
     * Parse XML content into ProcessedTaxInvoice domain model
     *
     * @param xmlContent The XML tax invoice content
     * @param sourceInvoiceId The source invoice ID from intake service
     * @return Parsed tax invoice domain model
     * @throws TaxInvoiceParsingException if parsing fails
     */
    ProcessedTaxInvoice parseInvoice(String xmlContent, String sourceInvoiceId) throws TaxInvoiceParsingException;

    /**
     * Exception thrown when tax invoice parsing fails
     */
    class TaxInvoiceParsingException extends Exception {
        public TaxInvoiceParsingException(String message) {
            super(message);
        }

        public TaxInvoiceParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
