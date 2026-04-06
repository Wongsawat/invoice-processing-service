package com.wpanther.invoice.processing.domain.event;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.Money;

import java.time.Instant;

/**
 * Domain event raised by ProcessedInvoice when processing completes.
 * Pure Java record — no framework or Kafka dependencies.
 * The application layer translates this into a Kafka DTO via InvoiceEventPublishingPort.
 *
 * <p>Use the static factory {@link #of} in production code so the field-to-argument
 * mapping is visible at the call site and {@code occurredAt} is stamped exactly once.
 * The canonical constructor is available for tests that require a fixed timestamp.
 */
public record InvoiceProcessedDomainEvent(
    InvoiceId documentId,
    String documentNumber,
    Money total,
    String sagaId,
    String correlationId,
    Instant occurredAt
) {
    public static InvoiceProcessedDomainEvent of(
            InvoiceId documentId,
            String documentNumber,
            Money total,
            String sagaId,
            String correlationId) {
        return new InvoiceProcessedDomainEvent(documentId, documentNumber, total, sagaId, correlationId, Instant.now());
    }
}
