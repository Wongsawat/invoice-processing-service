package com.invoice.processing.infrastructure.messaging;

import com.invoice.processing.application.service.InvoiceProcessingService;
import com.invoice.processing.domain.event.InvoiceReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvoiceEventListener
 */
@ExtendWith(MockitoExtension.class)
class InvoiceEventListenerTest {

    @Mock
    private InvoiceProcessingService processingService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private InvoiceEventListener eventListener;

    private InvoiceReceivedEvent validEvent;

    @BeforeEach
    void setUp() {
        validEvent = new InvoiceReceivedEvent(
            "intake-123",
            "INV-001",
            "<xml>test</xml>",
            "correlation-123"
        );
    }

    @Test
    void testHandleInvoiceReceivedSuccess() {
        // Given
        doNothing().when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(validEvent, 0, 100L, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(validEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testHandleInvoiceReceivedWithDifferentPartitionAndOffset() {
        // Given
        doNothing().when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(validEvent, 5, 999L, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(validEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testHandleInvoiceReceivedProcessingError() {
        // Given
        doThrow(new RuntimeException("Processing error"))
            .when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(validEvent, 0, 100L, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(validEvent);
        verify(acknowledgment, never()).acknowledge(); // Should not acknowledge on error
    }

    @Test
    void testHandleInvoiceReceivedMultipleEvents() {
        // Given
        InvoiceReceivedEvent event1 = new InvoiceReceivedEvent(
            "intake-1", "INV-1", "<xml>1</xml>", "corr-1"
        );
        InvoiceReceivedEvent event2 = new InvoiceReceivedEvent(
            "intake-2", "INV-2", "<xml>2</xml>", "corr-2"
        );

        doNothing().when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(event1, 0, 100L, acknowledgment);
        eventListener.handleInvoiceReceived(event2, 0, 101L, acknowledgment);

        // Then
        verify(processingService, times(2)).processInvoiceReceived(any(InvoiceReceivedEvent.class));
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    void testHandleInvoiceReceivedPartialFailure() {
        // Given
        InvoiceReceivedEvent event1 = new InvoiceReceivedEvent(
            "intake-1", "INV-1", "<xml>1</xml>", "corr-1"
        );
        InvoiceReceivedEvent event2 = new InvoiceReceivedEvent(
            "intake-2", "INV-2", "<xml>2</xml>", "corr-2"
        );

        // First call succeeds, second fails
        doNothing().doThrow(new RuntimeException("Error"))
            .when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(event1, 0, 100L, acknowledgment);
        eventListener.handleInvoiceReceived(event2, 0, 101L, acknowledgment);

        // Then
        verify(processingService, times(2)).processInvoiceReceived(any(InvoiceReceivedEvent.class));
        verify(acknowledgment, times(1)).acknowledge(); // Only first event acknowledged
    }

    @Test
    void testHandleInvoiceReceivedWithNullPointerException() {
        // Given
        doThrow(new NullPointerException("Null value"))
            .when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(validEvent, 0, 100L, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(validEvent);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void testHandleInvoiceReceivedWithIllegalStateException() {
        // Given
        doThrow(new IllegalStateException("Invalid state"))
            .when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(validEvent, 0, 100L, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(validEvent);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void testHandleInvoiceReceivedEventDetails() {
        // Given
        InvoiceReceivedEvent detailedEvent = new InvoiceReceivedEvent(
            "intake-detailed-123",
            "INV-DETAILED-001",
            "<xml>detailed content</xml>",
            "correlation-detailed-123"
        );

        doNothing().when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(detailedEvent, 3, 5000L, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(detailedEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testHandleInvoiceReceivedLargeOffset() {
        // Given
        long largeOffset = 999999999L;
        doNothing().when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(validEvent, 0, largeOffset, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(validEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testHandleInvoiceReceivedHighPartition() {
        // Given
        int highPartition = 100;
        doNothing().when(processingService).processInvoiceReceived(any(InvoiceReceivedEvent.class));

        // When
        eventListener.handleInvoiceReceived(validEvent, highPartition, 1L, acknowledgment);

        // Then
        verify(processingService).processInvoiceReceived(validEvent);
        verify(acknowledgment).acknowledge();
    }
}
