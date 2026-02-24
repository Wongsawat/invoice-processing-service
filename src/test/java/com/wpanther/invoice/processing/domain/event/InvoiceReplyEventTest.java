package com.wpanther.invoice.processing.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceReplyEventTest {

    @Test
    void success_createsEventWithSuccessStatus() {
        InvoiceReplyEvent event = InvoiceReplyEvent.success("saga-1", SagaStep.PROCESS_INVOICE, "corr-1");

        assertNotNull(event);
        assertEquals("saga-1", event.getSagaId());
        assertEquals(SagaStep.PROCESS_INVOICE, event.getSagaStep());
        assertEquals("corr-1", event.getCorrelationId());
        assertEquals(ReplyStatus.SUCCESS, event.getStatus());
        assertNull(event.getErrorMessage());
        assertTrue(event.isSuccess());
        assertFalse(event.isFailure());
        assertFalse(event.isCompensated());
    }

    @Test
    void failure_createsEventWithFailureStatusAndErrorMessage() {
        InvoiceReplyEvent event = InvoiceReplyEvent.failure(
            "saga-2", SagaStep.PROCESS_INVOICE, "corr-2", "XML parsing failed");

        assertNotNull(event);
        assertEquals("saga-2", event.getSagaId());
        assertEquals(SagaStep.PROCESS_INVOICE, event.getSagaStep());
        assertEquals("corr-2", event.getCorrelationId());
        assertEquals(ReplyStatus.FAILURE, event.getStatus());
        assertEquals("XML parsing failed", event.getErrorMessage());
        assertFalse(event.isSuccess());
        assertTrue(event.isFailure());
        assertFalse(event.isCompensated());
    }

    @Test
    void compensated_createsEventWithCompensatedStatus() {
        InvoiceReplyEvent event = InvoiceReplyEvent.compensated("saga-3", SagaStep.PROCESS_INVOICE, "corr-3");

        assertNotNull(event);
        assertEquals("saga-3", event.getSagaId());
        assertEquals(SagaStep.PROCESS_INVOICE, event.getSagaStep());
        assertEquals("corr-3", event.getCorrelationId());
        assertEquals(ReplyStatus.COMPENSATED, event.getStatus());
        assertNull(event.getErrorMessage());
        assertFalse(event.isSuccess());
        assertFalse(event.isFailure());
        assertTrue(event.isCompensated());
    }

    @Test
    void success_setsEventIdAndOccurredAt() {
        InvoiceReplyEvent event = InvoiceReplyEvent.success("saga-1", SagaStep.PROCESS_INVOICE, "corr-1");

        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void failure_setsEventIdAndOccurredAt() {
        InvoiceReplyEvent event = InvoiceReplyEvent.failure("saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "err");

        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void success_canBeSerializedToJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        InvoiceReplyEvent event = InvoiceReplyEvent.success("saga-1", SagaStep.PROCESS_INVOICE, "corr-1");

        String json = objectMapper.writeValueAsString(event);

        assertNotNull(json);
        assertTrue(json.contains("saga-1"));
        assertTrue(json.contains("corr-1"));
        assertTrue(json.contains("SUCCESS"));
        // SagaStep serializes to kebab-case code via @JsonValue
        assertTrue(json.contains("process-invoice"));
    }

    @Test
    void failure_canBeSerializedToJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        InvoiceReplyEvent event = InvoiceReplyEvent.failure("saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "error msg");

        String json = objectMapper.writeValueAsString(event);

        assertNotNull(json);
        assertTrue(json.contains("FAILURE"));
        assertTrue(json.contains("error msg"));
    }
}
