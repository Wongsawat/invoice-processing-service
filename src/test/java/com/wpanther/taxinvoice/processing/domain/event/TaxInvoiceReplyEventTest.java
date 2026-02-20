package com.wpanther.taxinvoice.processing.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaxInvoiceReplyEventTest {

    @Test
    void testSuccessReply() {
        TaxInvoiceReplyEvent reply = TaxInvoiceReplyEvent.success("saga-1", "process-tax-invoice", "corr-1");

        assertTrue(reply.isSuccess());
        assertFalse(reply.isFailure());
        assertFalse(reply.isCompensated());
        assertEquals(ReplyStatus.SUCCESS, reply.getStatus());
        assertNull(reply.getErrorMessage());
        assertEquals("saga-1", reply.getSagaId());
        assertEquals("process-tax-invoice", reply.getSagaStep());
        assertEquals("corr-1", reply.getCorrelationId());
    }

    @Test
    void testFailureReply() {
        TaxInvoiceReplyEvent reply = TaxInvoiceReplyEvent.failure(
            "saga-1", "process-tax-invoice", "corr-1", "Parse error"
        );

        assertFalse(reply.isSuccess());
        assertTrue(reply.isFailure());
        assertFalse(reply.isCompensated());
        assertEquals(ReplyStatus.FAILURE, reply.getStatus());
        assertEquals("Parse error", reply.getErrorMessage());
        assertEquals("saga-1", reply.getSagaId());
    }

    @Test
    void testCompensatedReply() {
        TaxInvoiceReplyEvent reply = TaxInvoiceReplyEvent.compensated(
            "saga-1", "COMPENSATE_process-tax-invoice", "corr-1"
        );

        assertFalse(reply.isSuccess());
        assertFalse(reply.isFailure());
        assertTrue(reply.isCompensated());
        assertEquals(ReplyStatus.COMPENSATED, reply.getStatus());
        assertNull(reply.getErrorMessage());
    }

    @Test
    void testInheritedFields() {
        TaxInvoiceReplyEvent reply = TaxInvoiceReplyEvent.success("saga-1", "step-1", "corr-1");

        assertNotNull(reply.getEventId());
        assertNotNull(reply.getOccurredAt());
        assertEquals(1, reply.getVersion());
    }

    @Test
    void testDifferentSagaIds() {
        TaxInvoiceReplyEvent reply1 = TaxInvoiceReplyEvent.success("saga-1", "step-1", "corr-1");
        TaxInvoiceReplyEvent reply2 = TaxInvoiceReplyEvent.success("saga-2", "step-1", "corr-2");

        assertNotEquals(reply1.getSagaId(), reply2.getSagaId());
        assertNotEquals(reply1.getCorrelationId(), reply2.getCorrelationId());
        assertNotEquals(reply1.getEventId(), reply2.getEventId());
    }
}
