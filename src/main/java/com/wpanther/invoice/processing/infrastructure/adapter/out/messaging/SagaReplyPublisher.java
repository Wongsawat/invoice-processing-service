package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging;

import com.wpanther.invoice.processing.infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent;
import com.wpanther.invoice.processing.application.port.out.SagaReplyPort;
import com.wpanther.invoice.processing.infrastructure.config.KafkaTopicsProperties;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Publishes saga reply events via outbox pattern.
 * Replies are sent to orchestrator via configurable saga.reply.invoice topic.
 */
@Component
@Slf4j
public class SagaReplyPublisher implements SagaReplyPort {

    private static final String AGGREGATE_TYPE = "ProcessedInvoice";

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;
    private final String replyTopic;

    /** Production constructor — Spring injects the bound {@link KafkaTopicsProperties}. */
    @Autowired
    public SagaReplyPublisher(
            OutboxService outboxService,
            HeaderSerializer headerSerializer,
            KafkaTopicsProperties topics) {
        this(outboxService, headerSerializer, topics.sagaReplyInvoice());
    }

    /** Package-private constructor for unit tests that pass the topic string directly. */
    SagaReplyPublisher(OutboxService outboxService, HeaderSerializer headerSerializer, String replyTopic) {
        this.outboxService = outboxService;
        this.headerSerializer = headerSerializer;
        this.replyTopic = replyTopic;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId) {
        InvoiceReplyEvent reply = InvoiceReplyEvent.success(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
            "sagaId", sagaId,
            "correlationId", correlationId,
            "status", "SUCCESS"
        );

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            replyTopic,
            sagaId,
            headerSerializer.toJson(headers)
        );

        log.info("Published SUCCESS saga reply for saga {} step {}", sagaId, sagaStep);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        InvoiceReplyEvent reply = InvoiceReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

        Map<String, String> headers = Map.of(
            "sagaId", sagaId,
            "correlationId", correlationId,
            "status", "FAILURE"
        );

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            replyTopic,
            sagaId,
            headerSerializer.toJson(headers)
        );

        log.info("Published FAILURE saga reply for saga {} step {}: {}", sagaId, sagaStep, errorMessage);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        InvoiceReplyEvent reply = InvoiceReplyEvent.compensated(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
            "sagaId", sagaId,
            "correlationId", correlationId,
            "status", "COMPENSATED"
        );

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            replyTopic,
            sagaId,
            headerSerializer.toJson(headers)
        );

        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
    }
}
