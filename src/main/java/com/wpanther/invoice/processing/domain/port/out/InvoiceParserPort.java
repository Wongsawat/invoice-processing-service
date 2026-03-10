package com.wpanther.invoice.processing.domain.port.out;

import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;

/**
 * Outbound port — parsing contract for invoice XML.
 * Domain dictates the contract; infrastructure provides the implementation.
 */
public interface InvoiceParserPort {

    /**
     * Parse XML content into ProcessedInvoice domain model
     *
     * @param xmlContent The XML invoice content
     * @param sourceInvoiceId The source invoice ID from intake service
     * @return Parsed invoice domain model
     * @throws InvoiceParsingException if parsing fails
     */
    ProcessedInvoice parse(String xmlContent, String sourceInvoiceId) throws InvoiceParsingException;

    /**
     * Exception thrown when invoice parsing fails
     */
    class InvoiceParsingException extends Exception {
        public InvoiceParsingException(String message) {
            super(message);
        }

        public InvoiceParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
