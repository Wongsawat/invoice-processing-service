package com.invoice.processing.infrastructure.messaging;

import com.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.invoice.processing.domain.event.PdfGenerationRequestedEvent;
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

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.invoice-processed}")
    private String invoiceProcessedTopic;

    @Value("${app.kafka.topics.pdf-generation-requested}")
    private String pdfGenerationRequestedTopic;

    /**
     * Publish invoice processed event
     */
    public void publishInvoiceProcessed(InvoiceProcessedEvent event) {
        log.info("Publishing invoice processed event for invoice: {}", event.getInvoiceNumber());

        CompletableFuture<SendResult<String, Object>> future =
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

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(pdfGenerationRequestedTopic, event.getInvoiceId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published PDF generation request: {}", event.getInvoiceNumber());
            } else {
                log.error("Failed to publish PDF generation request: {}", event.getInvoiceNumber(), ex);
            }
        });
    }
}
