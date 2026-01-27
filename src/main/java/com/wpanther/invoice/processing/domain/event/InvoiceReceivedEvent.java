package com.wpanther.invoice.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when an invoice is received from Intake Service
 */
@Getter
public class InvoiceReceivedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "invoice.received";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("correlationId")
    private final String correlationId;

    public InvoiceReceivedEvent(String documentId, String invoiceNumber, String xmlContent, String correlationId) {
        super(EVENT_TYPE);
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.correlationId = correlationId;
    }

    @JsonCreator
    public InvoiceReceivedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("xmlContent") String xmlContent,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.correlationId = correlationId;
    }
}
