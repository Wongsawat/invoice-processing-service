package com.wpanther.invoice.processing.application.port.out;

import com.wpanther.saga.domain.enums.SagaStep;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound port for publishing saga replies to the orchestrator.
 * Replies are published via the transactional outbox pattern for exactly-once delivery.
 * Implementations MUST require an active transaction (@Transactional(MANDATORY)).
 */
public interface SagaReplyPort {

    /**
     * Publish a saga reply indicating successful completion.
     *
     * @param sagaId the saga instance ID
     * @param sagaStep the step that completed
     * @param correlationId for tracing
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId);

    /**
     * Publish a saga reply indicating failure.
     *
     * @param sagaId the saga instance ID
     * @param sagaStep the step that failed
     * @param correlationId for tracing
     * @param errorMessage error details
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage);

    /**
     * Publish a saga reply indicating successful compensation (rollback).
     *
     * @param sagaId the saga instance ID
     * @param sagaStep the step that was compensated
     * @param correlationId for tracing
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
