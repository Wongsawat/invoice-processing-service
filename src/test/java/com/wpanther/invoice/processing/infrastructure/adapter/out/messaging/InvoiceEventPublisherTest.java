package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.Money;
import com.wpanther.invoice.processing.application.dto.event.InvoiceProcessedEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvoiceEventPublisher
 */
@ExtendWith(MockitoExtension.class)
class InvoiceEventPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    private InvoiceEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        HeaderSerializer headerSerializer = new HeaderSerializer(objectMapper);
        eventPublisher = new InvoiceEventPublisher(outboxService, headerSerializer, "invoice.processed");
    }

    @Test
    void publish_savesEventToOutboxWithCorrectTopic() throws JsonProcessingException {
        // Given
        InvoiceId invoiceId = InvoiceId.generate();
        InvoiceProcessedDomainEvent domainEvent = new InvoiceProcessedDomainEvent(
            invoiceId,
            "INV-DOM-001",
            Money.of(new BigDecimal("5000.00"), "THB"),
            "saga-dom-1",
            "corr-dom-1",
            Instant.now()
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

        // When
        eventPublisher.publish(domainEvent);

        // Then
        verify(outboxService).saveWithRouting(
            any(InvoiceProcessedEvent.class),
            eq("ProcessedInvoice"),
            eq(invoiceId.value().toString()),
            eq("invoice.processed"),
            eq(invoiceId.value().toString()),
            anyString()
        );
    }

    @Test
    void publish_usesInvoiceIdAsAggregateIdAndPartitionKey() throws JsonProcessingException {
        InvoiceId invoiceId = InvoiceId.generate();
        String expectedId = invoiceId.value().toString();
        InvoiceProcessedDomainEvent domainEvent = new InvoiceProcessedDomainEvent(
            invoiceId, "INV-DOM-002",
            Money.of(new BigDecimal("1000.00"), "THB"),
            "saga-dom-2", "corr-dom-2", Instant.now()
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

        eventPublisher.publish(domainEvent);

        verify(outboxService).saveWithRouting(
            any(), eq("ProcessedInvoice"), eq(expectedId), any(), eq(expectedId), any()
        );
    }

    @Test
    void publish_whenOutboxFails_propagatesException() throws JsonProcessingException {
        InvoiceProcessedDomainEvent domainEvent = new InvoiceProcessedDomainEvent(
            InvoiceId.generate(), "INV-DOM-ERR",
            Money.of(new BigDecimal("100.00"), "THB"),
            "saga-dom-err", "corr-dom-err", Instant.now()
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        doThrow(new RuntimeException("Outbox unavailable"))
            .when(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class, () -> eventPublisher.publish(domainEvent));
    }

    @Test
    void publish_headersFallbackWhenSerializationFails() throws JsonProcessingException {
        InvoiceProcessedDomainEvent domainEvent = new InvoiceProcessedDomainEvent(
            InvoiceId.generate(), "INV-DOM-HDR",
            Money.of(new BigDecimal("200.00"), "THB"),
            "saga-dom-hdr", "corr-dom-hdr", Instant.now()
        );
        when(objectMapper.writeValueAsString(any(Map.class))).thenThrow(JsonProcessingException.class);

        // Should not throw — HeaderSerializer falls back to "{}"
        eventPublisher.publish(domainEvent);

        verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), eq("{}"));
    }
}
