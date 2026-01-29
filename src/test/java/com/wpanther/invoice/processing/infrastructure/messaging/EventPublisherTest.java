package com.wpanther.invoice.processing.infrastructure.messaging;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.invoice.processing.domain.event.XmlSigningRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.camel.ProducerTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventPublisher
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private ProducerTemplate producerTemplate;

    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(producerTemplate);
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
        ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> headerValueCaptor = ArgumentCaptor.forClass(Object.class);

        verify(producerTemplate).sendBodyAndHeader(
            endpointCaptor.capture(),
            bodyCaptor.capture(),
            headerNameCaptor.capture(),
            headerValueCaptor.capture()
        );

        assertEquals("direct:publish-invoice-processed", endpointCaptor.getValue());
        assertEquals(event, bodyCaptor.getValue());
        assertEquals("kafka.KEY", headerNameCaptor.getValue());
        assertEquals("invoice-123", headerValueCaptor.getValue());
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

        doThrow(new RuntimeException("Camel error")).when(producerTemplate)
            .sendBodyAndHeader(anyString(), any(), anyString(), any());

        // When/Then
        assertThrows(RuntimeException.class, () -> eventPublisher.publishInvoiceProcessed(event));

        verify(producerTemplate).sendBodyAndHeader(
            eq("direct:publish-invoice-processed"),
            eq(event),
            eq("kafka.KEY"),
            eq("invoice-123")
        );
    }

    @Test
    void testPublishXmlSigningRequestedSuccess() {
        // Given
        XmlSigningRequestedEvent event = new XmlSigningRequestedEvent(
            "invoice-123",
            "INV-001",
            "<xml>content</xml>",
            "{\"data\":\"json\"}",
            "correlation-123"
        );

        // When
        eventPublisher.publishXmlSigningRequested(event);

        // Then
        ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> headerValueCaptor = ArgumentCaptor.forClass(Object.class);

        verify(producerTemplate).sendBodyAndHeader(
            endpointCaptor.capture(),
            bodyCaptor.capture(),
            headerNameCaptor.capture(),
            headerValueCaptor.capture()
        );

        assertEquals("direct:publish-xml-signing-requested", endpointCaptor.getValue());
        assertEquals(event, bodyCaptor.getValue());
        assertEquals("kafka.KEY", headerNameCaptor.getValue());
        assertEquals("invoice-123", headerValueCaptor.getValue());
    }

    @Test
    void testPublishXmlSigningRequestedFailure() {
        // Given
        XmlSigningRequestedEvent event = new XmlSigningRequestedEvent(
            "invoice-123",
            "INV-001",
            "<xml>content</xml>",
            "{\"data\":\"json\"}",
            "correlation-123"
        );

        doThrow(new RuntimeException("Camel error")).when(producerTemplate)
            .sendBodyAndHeader(anyString(), any(), anyString(), any());

        // When/Then
        assertThrows(RuntimeException.class, () -> eventPublisher.publishXmlSigningRequested(event));

        verify(producerTemplate).sendBodyAndHeader(
            eq("direct:publish-xml-signing-requested"),
            eq(event),
            eq("kafka.KEY"),
            eq("invoice-123")
        );
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

        // When
        eventPublisher.publishInvoiceProcessed(event);

        // Then
        verify(producerTemplate).sendBodyAndHeader(
            eq("direct:publish-invoice-processed"),
            eq(event),
            eq("kafka.KEY"),
            eq("invoice-456")
        );
    }

    @Test
    void testPublishXmlSigningRequestedWithCorrectKey() {
        // Given
        XmlSigningRequestedEvent event = new XmlSigningRequestedEvent(
            "invoice-999",
            "INV-004",
            "<xml>content</xml>",
            "{}",
            "correlation-999"
        );

        // When
        eventPublisher.publishXmlSigningRequested(event);

        // Then
        verify(producerTemplate).sendBodyAndHeader(
            eq("direct:publish-xml-signing-requested"),
            eq(event),
            eq("kafka.KEY"),
            eq("invoice-999")
        );
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

        // When
        eventPublisher.publishInvoiceProcessed(event1);
        eventPublisher.publishInvoiceProcessed(event2);

        // Then
        verify(producerTemplate, times(2)).sendBodyAndHeader(
            eq("direct:publish-invoice-processed"),
            any(),
            eq("kafka.KEY"),
            any()
        );
    }

    @Test
    void testMultiplePublishXmlSigningRequestedCalls() {
        // Given
        XmlSigningRequestedEvent event1 = new XmlSigningRequestedEvent(
            "invoice-1", "INV-1", "<xml>1</xml>", "{}", "corr-1"
        );
        XmlSigningRequestedEvent event2 = new XmlSigningRequestedEvent(
            "invoice-2", "INV-2", "<xml>2</xml>", "{}", "corr-2"
        );

        // When
        eventPublisher.publishXmlSigningRequested(event1);
        eventPublisher.publishXmlSigningRequested(event2);

        // Then
        verify(producerTemplate, times(2)).sendBodyAndHeader(
            eq("direct:publish-xml-signing-requested"),
            any(),
            eq("kafka.KEY"),
            any()
        );
    }
}
