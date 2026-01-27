package com.wpanther.invoice.processing.infrastructure.messaging;

import com.wpanther.invoice.processing.domain.event.IntegrationEvent;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.invoice.processing.domain.event.PdfGenerationRequestedEvent;
import com.wpanther.invoice.processing.domain.event.XmlSigningRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publisher for integration events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, IntegrationEvent> kafkaTemplate;

    @Value("${app.kafka.topics.invoice-processed}")
    private String invoiceProcessedTopic;

    @Value("${app.kafka.topics.pdf-generation-requested}")
    private String pdfGenerationRequestedTopic;

    @Value("${app.kafka.topics.xml-signing-requested}")
    private String xmlSigningRequestedTopic;

    /**
     * Publish invoice processed event
     */
    public void publishInvoiceProcessed(InvoiceProcessedEvent event) {
        log.info("Publishing invoice processed event for invoice: {}", event.getInvoiceNumber());

        CompletableFuture<SendResult<String, IntegrationEvent>> future =
            kafkaTemplate.send(invoiceProcessedTopic, event.getInvoiceId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published invoice processed event: {}", event.getInvoiceNumber());
            } else {
                log.error("Failed to publish invoice processed event: {}", event.getInvoiceNumber(), ex);
            }
        });
    }

    /**
     * Publish PDF generation requested event
     */
    public void publishPdfGenerationRequested(PdfGenerationRequestedEvent event) {
        log.info("Publishing PDF generation request for invoice: {}", event.getInvoiceNumber());

        CompletableFuture<SendResult<String, IntegrationEvent>> future =
            kafkaTemplate.send(pdfGenerationRequestedTopic, event.getInvoiceId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published PDF generation request: {}", event.getInvoiceNumber());
            } else {
                log.error("Failed to publish PDF generation request: {}", event.getInvoiceNumber(), ex);
            }
        });
    }

    /**
     * Publish XML signing requested event
     */
    public void publishXmlSigningRequested(XmlSigningRequestedEvent event) {
        log.info("Publishing XML signing request for invoice: {}", event.getInvoiceNumber());

        CompletableFuture<SendResult<String, IntegrationEvent>> future =
            kafkaTemplate.send(xmlSigningRequestedTopic, event.getInvoiceId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published XML signing request: {}", event.getInvoiceNumber());
            } else {
                log.error("Failed to publish XML signing request: {}", event.getInvoiceNumber(), ex);
            }
        });
    }
}
