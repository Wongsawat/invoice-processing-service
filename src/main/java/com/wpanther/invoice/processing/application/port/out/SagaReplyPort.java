package com.wpanther.invoice.processing.application.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Outbound port for publishing saga reply events.
 * Implemented by infrastructure adapters that send replies to the saga orchestrator
 * via the transactional outbox pattern for exactly-once delivery.
 */
public interface SagaReplyPort {

    /**
     * Publish a SUCCESS reply to the saga orchestrator.
     * Must be called within an active transaction (adapter uses {@code MANDATORY} propagation).
     *
     * @param sagaId        saga instance identifier
     * @param sagaStep      the step that completed successfully
     * @param correlationId end-to-end correlation ID for tracing
     */
    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId);

    /**
     * Publish a FAILURE reply to the saga orchestrator, triggering compensation.
     * Uses {@code REQUIRES_NEW} propagation — commits in its own independent transaction
     * even when the caller's transaction is marked {@code ROLLBACK_ONLY} or the Hibernate
     * session is in an invalid state after a DB error.
     *
     * @param sagaId        saga instance identifier
     * @param sagaStep      the step that failed
     * @param correlationId end-to-end correlation ID for tracing
     * @param errorMessage  human-readable failure description forwarded to the orchestrator
     */
    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage);

    /**
     * Publish a COMPENSATED reply to the saga orchestrator after a rollback.
     * Must be called within an active transaction (adapter uses {@code MANDATORY} propagation).
     *
     * @param sagaId        saga instance identifier
     * @param sagaStep      the step that was compensated
     * @param correlationId end-to-end correlation ID for tracing
     */
    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
