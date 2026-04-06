package com.wpanther.invoice.processing.application.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvoiceProcessedEvent
 */
class InvoiceProcessedEventTest {

    @Test
    void testCreateEvent() {
        // Given
        String documentId = "invoice-123";
        String documentNumber = "INV-001";
        BigDecimal totalAmount = new BigDecimal("10000.00");
        String currency = "THB";
        String sagaId = "saga-123";
        String correlationId = "correlation-123";

        // When
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            documentId, documentNumber, totalAmount, currency, sagaId, correlationId
        );

        // Then
        assertNotNull(event);
        assertEquals(documentId, event.getDocumentId());
        assertEquals(documentNumber, event.getDocumentNumber());
        assertEquals(totalAmount, event.getTotalAmount());
        assertEquals(currency, event.getCurrency());
        assertEquals(sagaId, event.getSagaId());
        assertEquals(correlationId, event.getCorrelationId());
        assertEquals("invoice.processed", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "invoice-123",
            "INV-001",
            new BigDecimal("10000.00"),
            "THB",
            "saga-123",
            "correlation-123"
        );

        // When
        String json = objectMapper.writeValueAsString(event);
        InvoiceProcessedEvent deserialized = objectMapper.readValue(json, InvoiceProcessedEvent.class);

        // Then
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals(event.getDocumentId(), deserialized.getDocumentId());
        assertEquals(event.getDocumentNumber(), deserialized.getDocumentNumber());
        assertEquals(event.getTotalAmount(), deserialized.getTotalAmount());
        assertEquals(event.getCurrency(), deserialized.getCurrency());
        assertEquals(event.getCorrelationId(), deserialized.getCorrelationId());
    }
}
