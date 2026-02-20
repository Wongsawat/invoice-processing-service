package com.wpanther.taxinvoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompensateTaxInvoiceCommandTest {

    @Test
    void testConvenienceConstructor() {
        CompensateTaxInvoiceCommand cmd = new CompensateTaxInvoiceCommand(
            "saga-1", "COMPENSATE_process-tax-invoice", "corr-1",
            "process-tax-invoice", "doc-1", "tax-invoice"
        );

        assertEquals("saga-1", cmd.getSagaId());
        assertEquals("COMPENSATE_process-tax-invoice", cmd.getSagaStep());
        assertEquals("corr-1", cmd.getCorrelationId());
        assertEquals("process-tax-invoice", cmd.getStepToCompensate());
        assertEquals("doc-1", cmd.getDocumentId());
        assertEquals("tax-invoice", cmd.getDocumentType());
        assertNotNull(cmd.getEventId());
        assertNotNull(cmd.getOccurredAt());
    }

    @Test
    void testFullConstructor() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        CompensateTaxInvoiceCommand cmd = new CompensateTaxInvoiceCommand(
            eventId, occurredAt, "CompensationCommand", 1,
            "saga-1", "COMPENSATE_process-tax-invoice", "corr-1",
            "process-tax-invoice", "doc-1", "tax-invoice"
        );

        assertEquals(eventId, cmd.getEventId());
        assertEquals(occurredAt, cmd.getOccurredAt());
        assertEquals("CompensationCommand", cmd.getEventType());
        assertEquals("process-tax-invoice", cmd.getStepToCompensate());
    }

    @Test
    void testJsonRoundTrip() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        CompensateTaxInvoiceCommand original = new CompensateTaxInvoiceCommand(
            "saga-1", "COMPENSATE_process-tax-invoice", "corr-1",
            "process-tax-invoice", "doc-1", "tax-invoice"
        );

        String json = objectMapper.writeValueAsString(original);
        CompensateTaxInvoiceCommand deserialized = objectMapper.readValue(json, CompensateTaxInvoiceCommand.class);

        assertEquals(original.getEventId(), deserialized.getEventId());
        assertEquals(original.getSagaId(), deserialized.getSagaId());
        assertEquals(original.getStepToCompensate(), deserialized.getStepToCompensate());
        assertEquals(original.getDocumentId(), deserialized.getDocumentId());
        assertEquals(original.getDocumentType(), deserialized.getDocumentType());
    }

    @Test
    void testEventType() {
        CompensateTaxInvoiceCommand cmd = new CompensateTaxInvoiceCommand(
            "saga-1", "COMPENSATE_process-tax-invoice", "corr-1",
            "process-tax-invoice", "doc-1", "tax-invoice"
        );

        assertEquals("CompensateTaxInvoiceCommand", cmd.getEventType());
    }
}
