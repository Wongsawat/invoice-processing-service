package com.wpanther.taxinvoice.processing.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Concrete SagaReply for the taxinvoice-processing-service.
 * Published to Kafka topic: saga.reply.tax-invoice
 */
public class TaxInvoiceReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    /**
     * Create a SUCCESS reply.
     */
    public static TaxInvoiceReplyEvent success(String sagaId, String sagaStep, String correlationId) {
        return new TaxInvoiceReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
    }

    /**
     * Create a FAILURE reply.
     */
    public static TaxInvoiceReplyEvent failure(String sagaId, String sagaStep, String correlationId,
                                                String errorMessage) {
        return new TaxInvoiceReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    /**
     * Create a COMPENSATED reply.
     */
    public static TaxInvoiceReplyEvent compensated(String sagaId, String sagaStep, String correlationId) {
        return new TaxInvoiceReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    // For SUCCESS and COMPENSATED (delegates to SagaReply 4-arg status constructor)
    private TaxInvoiceReplyEvent(String sagaId, String sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    // For FAILURE (delegates to SagaReply 4-arg error constructor)
    private TaxInvoiceReplyEvent(String sagaId, String sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }
}
