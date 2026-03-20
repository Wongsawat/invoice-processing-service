package com.wpanther.invoice.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port — driving adapter (Camel/Kafka) calls this to compensate (rollback) an invoice.
 * Implementation: application/service/InvoiceProcessingService.
 */
public interface CompensateInvoiceUseCase {

    /**
     * Compensate (rollback) a previously processed invoice.
     * Hard-deletes the ProcessedInvoice by documentId, then publishes COMPENSATED reply.
     * On failure, commits a FAILURE reply (via {@code REQUIRES_NEW}) and throws
     * {@link InvoiceCompensationException} so Camel's Dead Letter Channel can retry.
     *
     * @param documentId    Source document ID to delete
     * @param sagaId        Saga instance identifier
     * @param sagaStep      Current step in the saga
     * @param correlationId Correlation ID for tracing
     */
    void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId);

    /**
     * Exception thrown when invoice compensation fails.
     * Extends {@link RuntimeException} so Spring's {@code @Transactional} rolls back
     * automatically, and the exception propagates cleanly to Camel's Dead Letter Channel
     * without being replaced by {@code UnexpectedRollbackException}.
     */
    class InvoiceCompensationException extends RuntimeException {
        public InvoiceCompensationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
