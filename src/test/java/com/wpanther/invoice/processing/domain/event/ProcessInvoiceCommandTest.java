package com.wpanther.invoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessInvoiceCommandTest {

    @Test
    void convenienceConstructor_setsAllFields() {
        ProcessInvoiceCommand cmd = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-123", "<xml/>", "INV-001"
        );

        assertEquals("saga-1", cmd.getSagaId());
        assertEquals(SagaStep.PROCESS_INVOICE, cmd.getSagaStep());
        assertEquals("corr-1", cmd.getCorrelationId());
        assertEquals("doc-123", cmd.getDocumentId());
        assertEquals("<xml/>", cmd.getXmlContent());
        assertEquals("INV-001", cmd.getInvoiceNumber());
        assertNotNull(cmd.getEventId());
        assertNotNull(cmd.getOccurredAt());
    }

    @Test
    void fullConstructor_setsAllFields() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        ProcessInvoiceCommand cmd = new ProcessInvoiceCommand(
            eventId, now, "ProcessInvoiceCommand", 1,
            "saga-2", SagaStep.PROCESS_INVOICE, "corr-2", "doc-456", "<invoice/>", "INV-002"
        );

        assertEquals(eventId, cmd.getEventId());
        assertEquals(now, cmd.getOccurredAt());
        assertEquals("saga-2", cmd.getSagaId());
        assertEquals(SagaStep.PROCESS_INVOICE, cmd.getSagaStep());
        assertEquals("corr-2", cmd.getCorrelationId());
        assertEquals("doc-456", cmd.getDocumentId());
        assertEquals("<invoice/>", cmd.getXmlContent());
        assertEquals("INV-002", cmd.getInvoiceNumber());
    }

    @Test
    void jsonDeserialization_roundTrip() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        ProcessInvoiceCommand original = new ProcessInvoiceCommand(
            UUID.randomUUID(), Instant.now(), "ProcessInvoiceCommand", 1,
            "saga-3", SagaStep.PROCESS_INVOICE, "corr-3", "doc-789", "<xml>content</xml>", "INV-003"
        );

        String json = objectMapper.writeValueAsString(original);
        ProcessInvoiceCommand deserialized = objectMapper.readValue(json, ProcessInvoiceCommand.class);

        assertEquals(original.getEventId(), deserialized.getEventId());
        assertEquals(original.getSagaId(), deserialized.getSagaId());
        assertEquals(original.getSagaStep(), deserialized.getSagaStep());
        assertEquals(original.getCorrelationId(), deserialized.getCorrelationId());
        assertEquals(original.getDocumentId(), deserialized.getDocumentId());
        assertEquals(original.getXmlContent(), deserialized.getXmlContent());
        assertEquals(original.getInvoiceNumber(), deserialized.getInvoiceNumber());
    }

    @Test
    void convenienceConstructor_generatesUniqueEventIds() {
        ProcessInvoiceCommand cmd1 = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-1", "<xml/>", "INV-001"
        );
        ProcessInvoiceCommand cmd2 = new ProcessInvoiceCommand(
            "saga-2", SagaStep.PROCESS_INVOICE, "corr-2", "doc-2", "<xml/>", "INV-002"
        );

        assertNotEquals(cmd1.getEventId(), cmd2.getEventId());
    }
}
