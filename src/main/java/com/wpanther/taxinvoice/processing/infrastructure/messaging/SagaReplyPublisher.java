package com.wpanther.taxinvoice.processing.infrastructure.messaging;

import com.wpanther.taxinvoice.processing.domain.event.TaxInvoiceReplyEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Publishes saga reply events via the outbox pattern.
 * Replies are sent to the orchestrator via saga.reply.tax-invoice topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyPublisher {

    private static final String REPLY_TOPIC = "saga.reply.tax-invoice";
    private static final String AGGREGATE_TYPE = "ProcessedTaxInvoice";

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, String sagaStep, String correlationId) {
        TaxInvoiceReplyEvent reply = TaxInvoiceReplyEvent.success(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
            "sagaId", sagaId,
            "correlationId", correlationId,
            "status", "SUCCESS"
        );

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            REPLY_TOPIC,
            sagaId,
            headerSerializer.toJson(headers)
        );

        log.info("Published SUCCESS saga reply for saga {} step {}", sagaId, sagaStep);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishFailure(String sagaId, String sagaStep, String correlationId, String errorMessage) {
        TaxInvoiceReplyEvent reply = TaxInvoiceReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

        Map<String, String> headers = Map.of(
            "sagaId", sagaId,
            "correlationId", correlationId,
            "status", "FAILURE"
        );

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            REPLY_TOPIC,
            sagaId,
            headerSerializer.toJson(headers)
        );

        log.info("Published FAILURE saga reply for saga {} step {}: {}", sagaId, sagaStep, errorMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishCompensated(String sagaId, String sagaStep, String correlationId) {
        TaxInvoiceReplyEvent reply = TaxInvoiceReplyEvent.compensated(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
            "sagaId", sagaId,
            "correlationId", correlationId,
            "status", "COMPENSATED"
        );

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            REPLY_TOPIC,
            sagaId,
            headerSerializer.toJson(headers)
        );

        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
    }

}
