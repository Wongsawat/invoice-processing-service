package com.wpanther.invoice.processing.domain.event;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.Money;

import java.time.Instant;

/**
 * Domain event raised by ProcessedInvoice when processing completes.
 * Pure Java record — no framework or Kafka dependencies.
 * The application layer translates this into a Kafka DTO via InvoiceEventPublishingPort.
 */
public record InvoiceProcessedDomainEvent(
    InvoiceId invoiceId,
    String invoiceNumber,
    Money total,
    String correlationId,
    Instant occurredAt
) {}
