package com.wpanther.invoice.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port — driving adapter (Camel/Kafka) calls this to process an invoice.
 * Implementation: application/service/InvoiceProcessingService.
 */
public interface ProcessInvoiceUseCase {

    /**
     * Process an invoice from a saga command.
     * Handles idempotency, parses XML, persists, raises domain events, publishes saga reply.
     * Race conditions (DataIntegrityViolationException) are treated as idempotent success.
     *
     * <p>On any unrecoverable error the implementation commits a FAILURE reply to the outbox
     * (via a {@code REQUIRES_NEW} transaction in {@code SagaReplyPublisher}) and then throws
     * {@link InvoiceProcessingException} so that the calling Camel route knows the message was
     * handled and can commit the Kafka offset.
     *
     * @param documentId    Source document ID (used for idempotency)
     * @param xmlContent    Raw XML string to parse
     * @param sagaId        Saga instance identifier
     * @param sagaStep      Current step in the saga
     * @param correlationId Correlation ID for tracing
     * @throws InvoiceProcessingException after the FAILURE reply has been durably committed
     */
    void process(String documentId, String xmlContent,
                 String sagaId, SagaStep sagaStep, String correlationId)
            throws InvoiceProcessingException;

    /**
     * Exception thrown when invoice processing fails.
     * Always thrown <em>after</em> a FAILURE saga reply has been committed to the outbox,
     * so the Camel handler can catch it and return normally (committing the Kafka offset)
     * rather than letting it trigger an unwanted DLC retry.
     */
    class InvoiceProcessingException extends Exception {
        public InvoiceProcessingException(String message) {
            super(message);
        }

        public InvoiceProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
