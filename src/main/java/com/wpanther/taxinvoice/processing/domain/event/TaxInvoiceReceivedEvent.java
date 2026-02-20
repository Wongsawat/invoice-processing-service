package com.wpanther.taxinvoice.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a tax invoice is received from Intake Service
 */
@Getter
public class TaxInvoiceReceivedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "taxinvoice.received";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("correlationId")
    private final String correlationId;

    public TaxInvoiceReceivedEvent(String documentId, String invoiceNumber, String xmlContent, String correlationId) {
        super();
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public TaxInvoiceReceivedEvent(
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
