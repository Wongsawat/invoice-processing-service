package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging.dto;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Concrete SagaReply for invoice-processing-service.
 * Published to Kafka topic: saga.reply.invoice
 */
public class InvoiceReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    /**
     * Create a SUCCESS reply.
     */
    public static InvoiceReplyEvent success(String sagaId, SagaStep sagaStep, String correlationId) {
        return new InvoiceReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
    }

    /**
     * Create a FAILURE reply.
     */
    public static InvoiceReplyEvent failure(String sagaId, SagaStep sagaStep, String correlationId,
                                            String errorMessage) {
        return new InvoiceReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    /**
     * Create a COMPENSATED reply.
     */
    public static InvoiceReplyEvent compensated(String sagaId, SagaStep sagaStep, String correlationId) {
        return new InvoiceReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    // For SUCCESS and COMPENSATED (delegates to SagaReply 4-arg status constructor)
    private InvoiceReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    // For FAILURE (delegates to SagaReply 4-arg error constructor)
    private InvoiceReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }
}
