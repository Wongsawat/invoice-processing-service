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
     *
     * @param documentId    Source document ID to delete
     * @param sagaId        Saga instance identifier
     * @param sagaStep      Current step in the saga
     * @param correlationId Correlation ID for tracing
     */
    void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
