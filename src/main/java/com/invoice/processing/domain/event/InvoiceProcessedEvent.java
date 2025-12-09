package com.invoice.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when invoice processing is completed
 */
@Getter
public class InvoiceProcessedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "invoice.processed";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("total")
    private final BigDecimal total;

    @JsonProperty("currency")
    private final String currency;

    @JsonProperty("correlationId")
    private final String correlationId;

    public InvoiceProcessedEvent(String invoiceId, String invoiceNumber, BigDecimal total, String currency, String correlationId) {
        super(EVENT_TYPE);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.total = total;
        this.currency = currency;
        this.correlationId = correlationId;
    }

    @JsonCreator
    public InvoiceProcessedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("currency") String currency,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.total = total;
        this.currency = currency;
        this.correlationId = correlationId;
    }
}
