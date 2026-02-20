package com.wpanther.taxinvoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessTaxInvoiceCommandTest {

    @Test
    void testConvenienceConstructor() {
        ProcessTaxInvoiceCommand cmd = new ProcessTaxInvoiceCommand(
            "saga-1", "process-tax-invoice", "corr-1",
            "doc-1", "<xml>test</xml>", "TV-001"
        );

        assertEquals("saga-1", cmd.getSagaId());
        assertEquals("process-tax-invoice", cmd.getSagaStep());
        assertEquals("corr-1", cmd.getCorrelationId());
        assertEquals("doc-1", cmd.getDocumentId());
        assertEquals("<xml>test</xml>", cmd.getXmlContent());
        assertEquals("TV-001", cmd.getInvoiceNumber());
        assertNotNull(cmd.getEventId());
        assertNotNull(cmd.getOccurredAt());
    }

    @Test
    void testFullConstructor() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        ProcessTaxInvoiceCommand cmd = new ProcessTaxInvoiceCommand(
            eventId, occurredAt, "ProcessTaxInvoiceCommand", 1,
            "saga-1", "process-tax-invoice", "corr-1",
            "doc-1", "<xml>test</xml>", "TV-001"
        );

        assertEquals(eventId, cmd.getEventId());
        assertEquals(occurredAt, cmd.getOccurredAt());
        assertEquals("ProcessTaxInvoiceCommand", cmd.getEventType());
        assertEquals(1, cmd.getVersion());
        assertEquals("saga-1", cmd.getSagaId());
    }

    @Test
    void testJsonRoundTrip() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        ProcessTaxInvoiceCommand original = new ProcessTaxInvoiceCommand(
            "saga-1", "process-tax-invoice", "corr-1",
            "doc-1", "<xml>test</xml>", "TV-001"
        );

        String json = objectMapper.writeValueAsString(original);
        ProcessTaxInvoiceCommand deserialized = objectMapper.readValue(json, ProcessTaxInvoiceCommand.class);

        assertEquals(original.getEventId(), deserialized.getEventId());
        assertEquals(original.getSagaId(), deserialized.getSagaId());
        assertEquals(original.getSagaStep(), deserialized.getSagaStep());
        assertEquals(original.getCorrelationId(), deserialized.getCorrelationId());
        assertEquals(original.getDocumentId(), deserialized.getDocumentId());
        assertEquals(original.getXmlContent(), deserialized.getXmlContent());
        assertEquals(original.getInvoiceNumber(), deserialized.getInvoiceNumber());
    }

    @Test
    void testNullFields() {
        ProcessTaxInvoiceCommand cmd = new ProcessTaxInvoiceCommand(
            null, null, null, null, null, null
        );

        assertNull(cmd.getSagaId());
        assertNull(cmd.getDocumentId());
        assertNull(cmd.getXmlContent());
    }

    @Test
    void testEventType() {
        ProcessTaxInvoiceCommand cmd = new ProcessTaxInvoiceCommand(
            "saga-1", "process-tax-invoice", "corr-1",
            "doc-1", "<xml/>", "TV-001"
        );

        assertEquals("ProcessTaxInvoiceCommand", cmd.getEventType());
    }
}
