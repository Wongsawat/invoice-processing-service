package com.wpanther.invoice.processing.infrastructure.messaging;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.invoice.processing.domain.event.PdfGenerationRequestedEvent;
import com.wpanther.invoice.processing.domain.event.XmlSigningRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for integration events using Apache Camel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final ProducerTemplate producerTemplate;

    /**
     * Publish invoice processed event
     */
    public void publishInvoiceProcessed(InvoiceProcessedEvent event) {
        log.info("Publishing invoice processed event for invoice: {}", event.getInvoiceNumber());
        try {
            producerTemplate.sendBodyAndHeader(
                "direct:publish-invoice-processed",
                event,
                "kafka.KEY",
                event.getInvoiceId()
            );
            log.info("Successfully published invoice processed event: {}", event.getInvoiceNumber());
        } catch (Exception e) {
            log.error("Failed to publish invoice processed event: {}", event.getInvoiceNumber(), e);
            throw e;
        }
    }

    /**
     * Publish PDF generation requested event
     */
    public void publishPdfGenerationRequested(PdfGenerationRequestedEvent event) {
        log.info("Publishing PDF generation request for invoice: {}", event.getInvoiceNumber());
        try {
            producerTemplate.sendBodyAndHeader(
                "direct:publish-pdf-generation-requested",
                event,
                "kafka.KEY",
                event.getInvoiceId()
            );
            log.info("Successfully published PDF generation request: {}", event.getInvoiceNumber());
        } catch (Exception e) {
            log.error("Failed to publish PDF generation request: {}", event.getInvoiceNumber(), e);
            throw e;
        }
    }

    /**
     * Publish XML signing requested event
     */
    public void publishXmlSigningRequested(XmlSigningRequestedEvent event) {
        log.info("Publishing XML signing request for invoice: {}", event.getInvoiceNumber());
        try {
            producerTemplate.sendBodyAndHeader(
                "direct:publish-xml-signing-requested",
                event,
                "kafka.KEY",
                event.getInvoiceId()
            );
            log.info("Successfully published XML signing request: {}", event.getInvoiceNumber());
        } catch (Exception e) {
            log.error("Failed to publish XML signing request: {}", event.getInvoiceNumber(), e);
            throw e;
        }
    }
}
