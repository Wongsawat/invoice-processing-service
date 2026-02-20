package com.wpanther.invoice.processing.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

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
        eventPublisher = new EventPublisher(outboxService, headerSerializer);
    }

    @Test
    void testPublishInvoiceProcessedSuccess() {
        // Given
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "invoice-123",
            "INV-001",
            new BigDecimal("10000.00"),
            "THB",
            "correlation-123"
        );

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
}
