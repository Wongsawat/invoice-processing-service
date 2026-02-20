package com.wpanther.invoice.processing.domain.port;

/**
 * Domain port interface for publishing saga reply events.
 * Decouples the application layer from infrastructure messaging details.
 */
public interface SagaReplyPort {

    void publishSuccess(String sagaId, String sagaStep, String correlationId);

    void publishFailure(String sagaId, String sagaStep, String correlationId, String errorMessage);

    void publishCompensated(String sagaId, String sagaStep, String correlationId);
}
