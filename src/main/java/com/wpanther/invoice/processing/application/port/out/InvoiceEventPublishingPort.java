package com.wpanther.invoice.processing.application.port.out;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;

/**
 * Outbound port — application layer publishes the InvoiceProcessedDomainEvent.
 * Implementation: infrastructure/adapter/out/messaging/InvoiceEventPublisher.
 * The adapter translates the pure domain event into a Kafka DTO before writing to the outbox.
 */
public interface InvoiceEventPublishingPort {

    /**
     * Publish a domain event indicating an invoice was successfully processed.
     * Must be called within an active transaction (MANDATORY propagation on the adapter).
     */
    void publish(InvoiceProcessedDomainEvent event);
}
