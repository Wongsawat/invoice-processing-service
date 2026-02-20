package com.wpanther.taxinvoice.processing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.taxinvoice.processing.domain.event.TaxInvoiceReplyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaReplyPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    private SagaReplyPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SagaReplyPublisher(outboxService, objectMapper);
    }

    @Test
    void testPublishSuccessCallsOutboxWithCorrectParameters() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"sagaId\":\"saga-1\",\"correlationId\":\"corr-1\",\"status\":\"SUCCESS\"}");

        publisher.publishSuccess("saga-1", "process-tax-invoice", "corr-1");

        verify(outboxService).saveWithRouting(
            any(TaxInvoiceReplyEvent.class),
            eq("ProcessedTaxInvoice"),
            eq("saga-1"),
            eq("saga.reply.tax-invoice"),
            eq("saga-1"),
            contains("SUCCESS")
        );
    }

    @Test
    void testPublishSuccessUsesSagaIdAsPartitionKey() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publishSuccess("my-saga-id", "step-1", "corr-1");

        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
            any(), any(), any(), any(),
            partitionKeyCaptor.capture(),
            any()
        );

        assertEquals("my-saga-id", partitionKeyCaptor.getValue());
    }

    @Test
    void testPublishFailureCallsOutboxWithCorrectParameters() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"sagaId\":\"saga-1\",\"correlationId\":\"corr-1\",\"status\":\"FAILURE\"}");

        publisher.publishFailure("saga-1", "process-tax-invoice", "corr-1", "Parse error");

        verify(outboxService).saveWithRouting(
            any(TaxInvoiceReplyEvent.class),
            eq("ProcessedTaxInvoice"),
            eq("saga-1"),
            eq("saga.reply.tax-invoice"),
            eq("saga-1"),
            contains("FAILURE")
        );
    }

    @Test
    void testPublishCompensatedCallsOutboxWithCorrectParameters() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"sagaId\":\"saga-1\",\"correlationId\":\"corr-1\",\"status\":\"COMPENSATED\"}");

        publisher.publishCompensated("saga-1", "COMPENSATE_process-tax-invoice", "corr-1");

        verify(outboxService).saveWithRouting(
            any(TaxInvoiceReplyEvent.class),
            eq("ProcessedTaxInvoice"),
            eq("saga-1"),
            eq("saga.reply.tax-invoice"),
            eq("saga-1"),
            contains("COMPENSATED")
        );
    }

    @Test
    void testPublishSuccessHeadersContainCorrectFields() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"sagaId\":\"saga-1\",\"correlationId\":\"corr-1\",\"status\":\"SUCCESS\"}");

        publisher.publishSuccess("saga-1", "step-1", "corr-1");

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), headersCaptor.capture());

        String headers = headersCaptor.getValue();
        assertTrue(headers.contains("saga-1"));
        assertTrue(headers.contains("corr-1"));
        assertTrue(headers.contains("SUCCESS"));
    }

    @Test
    void testToJsonErrorReturnsNull() throws Exception {
        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("JSON error") {});

        publisher.publishSuccess("saga-1", "step-1", "corr-1");

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), headersCaptor.capture());

        assertNull(headersCaptor.getValue());
    }

    @Test
    void testPublishReplyEventHasCorrectTopic() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publishSuccess("saga-1", "step-1", "corr-1");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());

        assertEquals("saga.reply.tax-invoice", topicCaptor.getValue());
    }

    @Test
    void testPublishReplyEventHasCorrectAggregateType() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publishFailure("saga-1", "step-1", "corr-1", "error");

        ArgumentCaptor<String> aggregateTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), aggregateTypeCaptor.capture(), any(), any(), any(), any());

        assertEquals("ProcessedTaxInvoice", aggregateTypeCaptor.getValue());
    }
}
