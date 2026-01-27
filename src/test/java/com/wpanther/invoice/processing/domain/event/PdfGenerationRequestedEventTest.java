package com.wpanther.invoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PdfGenerationRequestedEvent
 */
class PdfGenerationRequestedEventTest {

    @Test
    void testCreateEvent() {
        // Given
        String invoiceId = "invoice-123";
        String invoiceNumber = "INV-001";
        String xmlContent = "<xml>test</xml>";
        String invoiceDataJson = "{\"test\":\"data\"}";
        String correlationId = "correlation-123";

        // When
        PdfGenerationRequestedEvent event = new PdfGenerationRequestedEvent(
            invoiceId, invoiceNumber, xmlContent, invoiceDataJson, correlationId
        );

        // Then
        assertNotNull(event);
        assertEquals(invoiceId, event.getInvoiceId());
        assertEquals(invoiceNumber, event.getInvoiceNumber());
        assertEquals(xmlContent, event.getXmlContent());
        assertEquals(invoiceDataJson, event.getInvoiceDataJson());
        assertEquals(correlationId, event.getCorrelationId());
        assertEquals("pdf.generation.requested", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        PdfGenerationRequestedEvent event = new PdfGenerationRequestedEvent(
            "invoice-123",
            "INV-001",
            "<xml>test</xml>",
            "{\"test\":\"data\"}",
            "correlation-123"
        );

        // When
        String json = objectMapper.writeValueAsString(event);
        PdfGenerationRequestedEvent deserialized = objectMapper.readValue(json, PdfGenerationRequestedEvent.class);

        // Then
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals(event.getInvoiceId(), deserialized.getInvoiceId());
        assertEquals(event.getInvoiceNumber(), deserialized.getInvoiceNumber());
        assertEquals(event.getXmlContent(), deserialized.getXmlContent());
        assertEquals(event.getInvoiceDataJson(), deserialized.getInvoiceDataJson());
        assertEquals(event.getCorrelationId(), deserialized.getCorrelationId());
    }
}
