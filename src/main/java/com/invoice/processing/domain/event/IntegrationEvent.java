package com.invoice.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all integration events
 */
@Getter
public abstract class IntegrationEvent implements Serializable {

    @JsonProperty("eventId")
    private final UUID eventId;

    @JsonProperty("occurredAt")
    private final Instant occurredAt;

    @JsonProperty("eventType")
    private final String eventType;

    @JsonProperty("version")
    private final int version;

    protected IntegrationEvent(String eventType) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.eventType = eventType;
        this.version = 1;
    }

    protected IntegrationEvent(UUID eventId, Instant occurredAt, String eventType, int version) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.version = version;
    }
}
