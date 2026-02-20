package com.wpanther.taxinvoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaxInvoiceReceivedEvent
 */
class TaxInvoiceReceivedEventTest {

    @Test
    void testCreateEvent() {
        // Given
        String documentId = "taxinvoice-123";
        String invoiceNumber = "TXN-001";
        String xmlContent = "<xml>test</xml>";
        String correlationId = "correlation-123";

        // When
        TaxInvoiceReceivedEvent event = new TaxInvoiceReceivedEvent(
            documentId, invoiceNumber, xmlContent, correlationId
        );

        // Then
        assertNotNull(event);
        assertEquals(documentId, event.getDocumentId());
        assertEquals(invoiceNumber, event.getInvoiceNumber());
        assertEquals(xmlContent, event.getXmlContent());
        assertEquals(correlationId, event.getCorrelationId());
        assertEquals("taxinvoice.received", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        TaxInvoiceReceivedEvent event = new TaxInvoiceReceivedEvent(
            "taxinvoice-123",
            "TXN-001",
            "<xml>test</xml>",
            "correlation-123"
        );

        // When
        String json = objectMapper.writeValueAsString(event);
        TaxInvoiceReceivedEvent deserialized = objectMapper.readValue(json, TaxInvoiceReceivedEvent.class);

        // Then
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals(event.getDocumentId(), deserialized.getDocumentId());
        assertEquals(event.getInvoiceNumber(), deserialized.getInvoiceNumber());
        assertEquals(event.getXmlContent(), deserialized.getXmlContent());
        assertEquals(event.getCorrelationId(), deserialized.getCorrelationId());
    }
}
