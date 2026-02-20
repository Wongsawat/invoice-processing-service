package com.wpanther.taxinvoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaxInvoiceProcessedEvent
 */
class TaxInvoiceProcessedEventTest {

    @Test
    void testCreateEvent() {
        // Given
        String invoiceId = "taxinvoice-123";
        String invoiceNumber = "TXN-001";
        BigDecimal total = new BigDecimal("10000.00");
        String currency = "THB";
        String correlationId = "correlation-123";

        // When
        TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
            invoiceId, invoiceNumber, total, currency, correlationId
        );

        // Then
        assertNotNull(event);
        assertEquals(invoiceId, event.getInvoiceId());
        assertEquals(invoiceNumber, event.getInvoiceNumber());
        assertEquals(total, event.getTotal());
        assertEquals(currency, event.getCurrency());
        assertEquals(correlationId, event.getCorrelationId());
        assertEquals("taxinvoice.processed", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
            "taxinvoice-123",
            "TXN-001",
            new BigDecimal("10000.00"),
            "THB",
            "correlation-123"
        );

        // When
        String json = objectMapper.writeValueAsString(event);
        TaxInvoiceProcessedEvent deserialized = objectMapper.readValue(json, TaxInvoiceProcessedEvent.class);

        // Then
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals(event.getInvoiceId(), deserialized.getInvoiceId());
        assertEquals(event.getInvoiceNumber(), deserialized.getInvoiceNumber());
        assertEquals(event.getTotal(), deserialized.getTotal());
        assertEquals(event.getCurrency(), deserialized.getCurrency());
        assertEquals(event.getCorrelationId(), deserialized.getCorrelationId());
    }
}
