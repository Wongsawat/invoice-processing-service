package com.wpanther.invoice.processing.domain.port;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Domain port interface for publishing saga reply events.
 * Decouples the application layer from infrastructure messaging details.
 */
public interface SagaReplyPort {

    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId);

    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage);

    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
