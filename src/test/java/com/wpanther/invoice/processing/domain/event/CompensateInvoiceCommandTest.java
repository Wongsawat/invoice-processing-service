package com.wpanther.invoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompensateInvoiceCommandTest {

    @Test
    void convenienceConstructor_setsAllFields() {
        CompensateInvoiceCommand cmd = new CompensateInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1",
            "process-invoice", "doc-123", "invoice"
        );

        assertEquals("saga-1", cmd.getSagaId());
        assertEquals(SagaStep.PROCESS_INVOICE, cmd.getSagaStep());
        assertEquals("corr-1", cmd.getCorrelationId());
        assertEquals("process-invoice", cmd.getStepToCompensate());
        assertEquals("doc-123", cmd.getDocumentId());
        assertEquals("invoice", cmd.getDocumentType());
        assertNotNull(cmd.getEventId());
        assertNotNull(cmd.getOccurredAt());
    }

    @Test
    void fullConstructor_setsAllFields() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        CompensateInvoiceCommand cmd = new CompensateInvoiceCommand(
            eventId, now, "CompensateInvoiceCommand", 1,
            "saga-2", SagaStep.PROCESS_INVOICE, "corr-2",
            "process-invoice", "doc-456", "invoice"
        );

        assertEquals(eventId, cmd.getEventId());
        assertEquals(now, cmd.getOccurredAt());
        assertEquals("saga-2", cmd.getSagaId());
        assertEquals(SagaStep.PROCESS_INVOICE, cmd.getSagaStep());
        assertEquals("corr-2", cmd.getCorrelationId());
        assertEquals("process-invoice", cmd.getStepToCompensate());
        assertEquals("doc-456", cmd.getDocumentId());
        assertEquals("invoice", cmd.getDocumentType());
    }

    @Test
    void jsonDeserialization_roundTrip() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        CompensateInvoiceCommand original = new CompensateInvoiceCommand(
            UUID.randomUUID(), Instant.now(), "CompensateInvoiceCommand", 1,
            "saga-3", SagaStep.PROCESS_INVOICE, "corr-3",
            "process-invoice", "doc-789", "invoice"
        );

        String json = objectMapper.writeValueAsString(original);
        CompensateInvoiceCommand deserialized = objectMapper.readValue(json, CompensateInvoiceCommand.class);

        assertEquals(original.getEventId(), deserialized.getEventId());
        assertEquals(original.getSagaId(), deserialized.getSagaId());
        assertEquals(original.getSagaStep(), deserialized.getSagaStep());
        assertEquals(original.getCorrelationId(), deserialized.getCorrelationId());
        assertEquals(original.getStepToCompensate(), deserialized.getStepToCompensate());
        assertEquals(original.getDocumentId(), deserialized.getDocumentId());
        assertEquals(original.getDocumentType(), deserialized.getDocumentType());
    }

    @Test
    void convenienceConstructor_generatesUniqueEventIds() {
        CompensateInvoiceCommand cmd1 = new CompensateInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "process-invoice", "doc-1", "invoice"
        );
        CompensateInvoiceCommand cmd2 = new CompensateInvoiceCommand(
            "saga-2", SagaStep.PROCESS_INVOICE, "corr-2", "process-invoice", "doc-2", "invoice"
        );

        assertNotEquals(cmd1.getEventId(), cmd2.getEventId());
    }
}
