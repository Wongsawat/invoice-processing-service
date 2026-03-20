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
     * Exception thrown when invoice parsing fails.
     *
     * <p>Use the static factory methods for the common parse-phase failures so that
     * message strings are defined in one place and callers stay readable.
     */
    class InvoiceParsingException extends Exception {
        public InvoiceParsingException(String message) {
            super(message);
        }

        public InvoiceParsingException(String message, Throwable cause) {
            super(message, cause);
        }

        /** Null or blank XML input. */
        public static InvoiceParsingException forEmpty() {
            return new InvoiceParsingException("XML content is null or empty");
        }

        /** Payload exceeds the configured size limit. */
        public static InvoiceParsingException forOversized(int byteSize, int limitBytes) {
            return new InvoiceParsingException(
                "XML payload too large: " + byteSize + " bytes (limit " + limitBytes + " bytes / 500 KB)");
        }

        /** JAXB unmarshal did not finish within the allowed wall-clock window. */
        public static InvoiceParsingException forTimeout(long timeoutMs) {
            return new InvoiceParsingException(
                "XML parsing timed out after " + timeoutMs + " ms — possible malformed input");
        }

        /** The executor thread was interrupted before unmarshal completed. */
        public static InvoiceParsingException forInterrupted() {
            return new InvoiceParsingException("XML parsing was interrupted");
        }

        /** JAXB or SAX threw an exception during unmarshal. */
        public static InvoiceParsingException forUnmarshal(Throwable cause) {
            return new InvoiceParsingException("XML parsing failed: " + cause.getMessage(), cause);
        }

        /** Unmarshalled object is not the expected root type. */
        public static InvoiceParsingException forUnexpectedRootElement(String className) {
            return new InvoiceParsingException("Unexpected root element: " + className);
        }
    }
}
