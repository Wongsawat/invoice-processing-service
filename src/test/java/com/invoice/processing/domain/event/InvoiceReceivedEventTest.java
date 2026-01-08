package com.invoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvoiceReceivedEvent
 */
class InvoiceReceivedEventTest {

    @Test
    void testCreateEvent() {
        // Given
        String invoiceId = "invoice-123";
        String invoiceNumber = "INV-001";
        String xmlContent = "<xml>test</xml>";
        String correlationId = "correlation-123";

        // When
        InvoiceReceivedEvent event = new InvoiceReceivedEvent(
            invoiceId, invoiceNumber, xmlContent, correlationId
        );

        // Then
        assertNotNull(event);
        assertEquals(invoiceId, event.getInvoiceId());
        assertEquals(invoiceNumber, event.getInvoiceNumber());
        assertEquals(xmlContent, event.getXmlContent());
        assertEquals(correlationId, event.getCorrelationId());
        assertEquals("invoice.received", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        InvoiceReceivedEvent event = new InvoiceReceivedEvent(
            "invoice-123",
            "INV-001",
            "<xml>test</xml>",
            "correlation-123"
        );

        // When
        String json = objectMapper.writeValueAsString(event);
        InvoiceReceivedEvent deserialized = objectMapper.readValue(json, InvoiceReceivedEvent.class);

        // Then
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals(event.getInvoiceId(), deserialized.getInvoiceId());
        assertEquals(event.getInvoiceNumber(), deserialized.getInvoiceNumber());
        assertEquals(event.getXmlContent(), deserialized.getXmlContent());
        assertEquals(event.getCorrelationId(), deserialized.getCorrelationId());
    }
}
