package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging;

import com.wpanther.invoice.processing.application.port.out.InvoiceEventPublishingPort;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.application.dto.event.InvoiceProcessedEvent;
import com.wpanther.invoice.processing.infrastructure.config.KafkaTopicsProperties;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@Slf4j
public class InvoiceEventPublisher implements InvoiceEventPublishingPort {

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;
    private final String invoiceProcessedTopic;

    /** Production constructor — Spring injects the bound {@link KafkaTopicsProperties}. */
    @Autowired
    public InvoiceEventPublisher(
            OutboxService outboxService,
            HeaderSerializer headerSerializer,
            KafkaTopicsProperties topics) {
        this(outboxService, headerSerializer, topics.invoiceProcessed());
    }

    /** Package-private constructor for unit tests that pass the topic string directly. */
    InvoiceEventPublisher(OutboxService outboxService, HeaderSerializer headerSerializer,
                   String invoiceProcessedTopic) {
        this.outboxService = outboxService;
        this.headerSerializer = headerSerializer;
        this.invoiceProcessedTopic = invoiceProcessedTopic;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(InvoiceProcessedDomainEvent domainEvent) {
        InvoiceProcessedEvent kafkaEvent = new InvoiceProcessedEvent(
            domainEvent.invoiceId().toString(),
            domainEvent.invoiceNumber(),
            domainEvent.total().amount(),
            domainEvent.total().currency(),
            domainEvent.correlationId()
        );

        Map<String, String> headers = Map.of(
            "correlationId", domainEvent.correlationId(),
            "invoiceNumber", domainEvent.invoiceNumber()
        );

        outboxService.saveWithRouting(
            kafkaEvent,
            "ProcessedInvoice",
            domainEvent.invoiceId().toString(),
            invoiceProcessedTopic,
            domainEvent.invoiceId().toString(),
            headerSerializer.toJson(headers)
        );

        log.info("Published InvoiceProcessedEvent to outbox: {}", domainEvent.invoiceNumber());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishInvoiceProcessed(InvoiceProcessedEvent event) {
        Map<String, String> headers = Map.of(
            "correlationId", event.getCorrelationId(),
            "invoiceNumber", event.getInvoiceNumber()
        );

        outboxService.saveWithRouting(
            event,
            "ProcessedInvoice",
            event.getInvoiceId(),
            invoiceProcessedTopic,
            event.getInvoiceId(),
            headerSerializer.toJson(headers)
        );

        log.info("Published InvoiceProcessedEvent to outbox: {}", event.getInvoiceNumber());
    }
}
