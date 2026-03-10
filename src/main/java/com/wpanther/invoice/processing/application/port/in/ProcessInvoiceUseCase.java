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
     * @param documentId    Source document ID (used for idempotency)
     * @param xmlContent    Raw XML string to parse
     * @param sagaId        Saga instance identifier
     * @param sagaStep      Current step in the saga
     * @param correlationId Correlation ID for tracing
     */
    void process(String documentId, String xmlContent,
                 String sagaId, SagaStep sagaStep, String correlationId);
}
