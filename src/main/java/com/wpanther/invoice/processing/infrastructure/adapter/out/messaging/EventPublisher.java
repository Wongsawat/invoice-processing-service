package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging;

import com.wpanther.invoice.processing.application.port.out.InvoiceEventPublishingPort;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher implements InvoiceEventPublishingPort {

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;

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
            "invoice.processed",
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
            "invoice.processed",
            event.getInvoiceId(),
            headerSerializer.toJson(headers)
        );

        log.info("Published InvoiceProcessedEvent to outbox: {}", event.getInvoiceNumber());
    }
}
