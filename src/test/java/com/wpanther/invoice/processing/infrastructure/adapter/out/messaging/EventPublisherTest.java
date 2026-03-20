package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.processing.application.dto.event.InvoiceProcessedEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventPublisher
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        HeaderSerializer headerSerializer = new HeaderSerializer(objectMapper);
        eventPublisher = new EventPublisher(outboxService, headerSerializer, "invoice.processed");
    }

    @Test
    void testPublishInvoiceProcessedSuccess() throws JsonProcessingException {
        // Given
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "invoice-123",
            "INV-001",
            new BigDecimal("10000.00"),
            "THB",
            "correlation-123"
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"correlationId\":\"correlation-123\",\"invoiceNumber\":\"INV-001\"}");

        // When
        eventPublisher.publishInvoiceProcessed(event);

        // Then
        verify(outboxService).saveWithRouting(
            eq(event),
            eq("ProcessedInvoice"),
            eq("invoice-123"),
            eq("invoice.processed"),
            eq("invoice-123"),
            anyString()
        );
    }

    @Test
    void testPublishInvoiceProcessed_usesCorrectTopic() throws JsonProcessingException {
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "inv-456", "INV-002", new BigDecimal("5000.00"), "THB", "corr-456"
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

        eventPublisher.publishInvoiceProcessed(event);

        verify(outboxService).saveWithRouting(
            any(), any(), any(), eq("invoice.processed"), any(), any()
        );
    }

    @Test
    void testPublishInvoiceProcessed_usesInvoiceIdAsPartitionKey() throws JsonProcessingException {
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "inv-789", "INV-003", new BigDecimal("2000.00"), "THB", "corr-789"
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

        eventPublisher.publishInvoiceProcessed(event);

        verify(outboxService).saveWithRouting(
            any(),
            eq("ProcessedInvoice"),
            eq("inv-789"),
            eq("invoice.processed"),
            eq("inv-789"),
            any()
        );
    }

    @Test
    void testPublishInvoiceProcessed_whenOutboxFails_propagatesException() throws JsonProcessingException {
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "inv-err", "INV-ERR", new BigDecimal("1000.00"), "THB", "corr-err"
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        doThrow(new RuntimeException("Outbox unavailable"))
            .when(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class,
            () -> eventPublisher.publishInvoiceProcessed(event));
    }

    @Test
    void testPublishInvoiceProcessed_headersFallbackWhenSerializationFails() throws JsonProcessingException {
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "inv-hdr", "INV-HDR", new BigDecimal("3000.00"), "THB", "corr-hdr"
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenThrow(JsonProcessingException.class);

        // Should not throw - HeaderSerializer falls back to "{}"
        eventPublisher.publishInvoiceProcessed(event);

        verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), eq("{}"));
    }
}
