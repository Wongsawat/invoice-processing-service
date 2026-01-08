package com.invoice.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to request XML signing
 */
@Getter
public class XmlSigningRequestedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "xml.signing.requested";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("invoiceDataJson")
    private final String invoiceDataJson;

    @JsonProperty("correlationId")
    private final String correlationId;

    public XmlSigningRequestedEvent(String invoiceId, String invoiceNumber, String xmlContent,
                                    String invoiceDataJson, String correlationId) {
        super(EVENT_TYPE);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.correlationId = correlationId;
    }

    @JsonCreator
    public XmlSigningRequestedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("xmlContent") String xmlContent,
        @JsonProperty("invoiceDataJson") String invoiceDataJson,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.correlationId = correlationId;
    }
}
