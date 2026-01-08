package com.invoice.processing.infrastructure.messaging;

import com.invoice.processing.domain.event.IntegrationEvent;
import com.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.invoice.processing.domain.event.PdfGenerationRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventPublisher
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, IntegrationEvent> kafkaTemplate;

    @Mock
    private SendResult<String, IntegrationEvent> sendResult;

    private EventPublisher eventPublisher;

    private static final String INVOICE_PROCESSED_TOPIC = "invoice.processed";
    private static final String PDF_GENERATION_REQUESTED_TOPIC = "pdf.generation.requested";

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(kafkaTemplate);

        // Set topic values using reflection
        ReflectionTestUtils.setField(eventPublisher, "invoiceProcessedTopic", INVOICE_PROCESSED_TOPIC);
        ReflectionTestUtils.setField(eventPublisher, "pdfGenerationRequestedTopic", PDF_GENERATION_REQUESTED_TOPIC);
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

        CompletableFuture<SendResult<String, IntegrationEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishInvoiceProcessed(event);

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IntegrationEvent> eventCaptor = ArgumentCaptor.forClass(IntegrationEvent.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        assertEquals(INVOICE_PROCESSED_TOPIC, topicCaptor.getValue());
        assertEquals("invoice-123", keyCaptor.getValue());
        assertEquals(event, eventCaptor.getValue());
    }

    @Test
    void testPublishInvoiceProcessedFailure() {
        // Given
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "invoice-123",
            "INV-001",
            new BigDecimal("10000.00"),
            "THB",
            "correlation-123"
        );

        CompletableFuture<SendResult<String, IntegrationEvent>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishInvoiceProcessed(event);

        // Then
        verify(kafkaTemplate).send(eq(INVOICE_PROCESSED_TOPIC), eq("invoice-123"), eq(event));
        // The error is logged but not thrown
    }

    @Test
    void testPublishPdfGenerationRequestedSuccess() {
        // Given
        PdfGenerationRequestedEvent event = new PdfGenerationRequestedEvent(
            "invoice-123",
            "INV-001",
            "<xml>content</xml>",
            "{\"data\":\"json\"}",
            "correlation-123"
        );

        CompletableFuture<SendResult<String, IntegrationEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishPdfGenerationRequested(event);

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IntegrationEvent> eventCaptor = ArgumentCaptor.forClass(IntegrationEvent.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        assertEquals(PDF_GENERATION_REQUESTED_TOPIC, topicCaptor.getValue());
        assertEquals("invoice-123", keyCaptor.getValue());
        assertEquals(event, eventCaptor.getValue());
    }

    @Test
    void testPublishPdfGenerationRequestedFailure() {
        // Given
        PdfGenerationRequestedEvent event = new PdfGenerationRequestedEvent(
            "invoice-123",
            "INV-001",
            "<xml>content</xml>",
            "{\"data\":\"json\"}",
            "correlation-123"
        );

        CompletableFuture<SendResult<String, IntegrationEvent>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishPdfGenerationRequested(event);

        // Then
        verify(kafkaTemplate).send(eq(PDF_GENERATION_REQUESTED_TOPIC), eq("invoice-123"), eq(event));
        // The error is logged but not thrown
    }

    @Test
    void testPublishInvoiceProcessedWithCorrectKey() {
        // Given
        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            "invoice-456",
            "INV-002",
            new BigDecimal("20000.00"),
            "USD",
            "correlation-456"
        );

        CompletableFuture<SendResult<String, IntegrationEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishInvoiceProcessed(event);

        // Then
        verify(kafkaTemplate).send(eq(INVOICE_PROCESSED_TOPIC), eq("invoice-456"), eq(event));
    }

    @Test
    void testPublishPdfGenerationRequestedWithCorrectKey() {
        // Given
        PdfGenerationRequestedEvent event = new PdfGenerationRequestedEvent(
            "invoice-789",
            "INV-003",
            "<xml>content</xml>",
            "{}",
            "correlation-789"
        );

        CompletableFuture<SendResult<String, IntegrationEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishPdfGenerationRequested(event);

        // Then
        verify(kafkaTemplate).send(eq(PDF_GENERATION_REQUESTED_TOPIC), eq("invoice-789"), eq(event));
    }

    @Test
    void testMultiplePublishInvoiceProcessedCalls() {
        // Given
        InvoiceProcessedEvent event1 = new InvoiceProcessedEvent(
            "invoice-1", "INV-1", new BigDecimal("100.00"), "THB", "corr-1"
        );
        InvoiceProcessedEvent event2 = new InvoiceProcessedEvent(
            "invoice-2", "INV-2", new BigDecimal("200.00"), "THB", "corr-2"
        );

        CompletableFuture<SendResult<String, IntegrationEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishInvoiceProcessed(event1);
        eventPublisher.publishInvoiceProcessed(event2);

        // Then
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
    }

    @Test
    void testMultiplePublishPdfGenerationRequestedCalls() {
        // Given
        PdfGenerationRequestedEvent event1 = new PdfGenerationRequestedEvent(
            "invoice-1", "INV-1", "<xml>1</xml>", "{}", "corr-1"
        );
        PdfGenerationRequestedEvent event2 = new PdfGenerationRequestedEvent(
            "invoice-2", "INV-2", "<xml>2</xml>", "{}", "corr-2"
        );

        CompletableFuture<SendResult<String, IntegrationEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        eventPublisher.publishPdfGenerationRequested(event1);
        eventPublisher.publishPdfGenerationRequested(event2);

        // Then
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
    }
}
