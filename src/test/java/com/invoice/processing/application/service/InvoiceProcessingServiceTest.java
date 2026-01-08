package com.invoice.processing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.invoice.processing.domain.event.InvoiceReceivedEvent;
import com.invoice.processing.domain.event.PdfGenerationRequestedEvent;
import com.invoice.processing.domain.model.*;
import com.invoice.processing.domain.repository.ProcessedInvoiceRepository;
import com.invoice.processing.domain.service.InvoiceParserService;
import com.invoice.processing.infrastructure.messaging.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvoiceProcessingService
 */
@ExtendWith(MockitoExtension.class)
class InvoiceProcessingServiceTest {

    @Mock
    private ProcessedInvoiceRepository invoiceRepository;

    @Mock
    private InvoiceParserService parserService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InvoiceProcessingService service;

    private InvoiceReceivedEvent validEvent;
    private ProcessedInvoice validInvoice;

    @BeforeEach
    void setUp() {
        // Setup valid event
        validEvent = new InvoiceReceivedEvent(
            "intake-123",
            "INV-001",
            "<xml>test</xml>",
            "correlation-123"
        );

        // Setup valid invoice
        Party seller = Party.of(
            "Seller Company",
            TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 Street", "Bangkok", "10110", "TH")
        );

        Party buyer = Party.of(
            "Buyer Company",
            TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Road", "Chiang Mai", "50000", "TH")
        );

        LineItem item = new LineItem(
            "Service 1",
            10,
            Money.of(1000.00, "THB"),
            new BigDecimal("7.00")
        );

        validInvoice = ProcessedInvoice.builder()
            .id(InvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(seller)
            .buyer(buyer)
            .addItem(item)
            .currency("THB")
            .originalXml("<xml>test</xml>")
            .build();
    }

    @Test
    void testProcessInvoiceReceivedSuccess() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        service.processInvoiceReceived(validEvent);

        // Then
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService).parseInvoice("<xml>test</xml>", "intake-123");
        verify(invoiceRepository, times(3)).save(any(ProcessedInvoice.class));
        verify(eventPublisher).publishInvoiceProcessed(any(InvoiceProcessedEvent.class));
        verify(eventPublisher).publishPdfGenerationRequested(any(PdfGenerationRequestedEvent.class));
    }

    @Test
    void testProcessInvoiceReceivedAlreadyProcessed() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.of(validInvoice));

        // When
        service.processInvoiceReceived(validEvent);

        // Then
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService, never()).parseInvoice(anyString(), anyString());
        verify(invoiceRepository, never()).save(any(ProcessedInvoice.class));
        verify(eventPublisher, never()).publishInvoiceProcessed(any());
    }

    @Test
    void testProcessInvoiceReceivedParsingError() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString()))
            .thenThrow(new InvoiceParserService.InvoiceParsingException("Parse error"));

        // When
        service.processInvoiceReceived(validEvent);

        // Then
        verify(parserService).parseInvoice("<xml>test</xml>", "intake-123");
        verify(invoiceRepository, never()).save(any(ProcessedInvoice.class));
        verify(eventPublisher, never()).publishInvoiceProcessed(any());
    }

    @Test
    void testProcessInvoiceReceivedPublishesCorrectEvents() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":\"data\"}");

        // When
        service.processInvoiceReceived(validEvent);

        // Then
        ArgumentCaptor<InvoiceProcessedEvent> processedEventCaptor =
            ArgumentCaptor.forClass(InvoiceProcessedEvent.class);
        verify(eventPublisher).publishInvoiceProcessed(processedEventCaptor.capture());

        InvoiceProcessedEvent processedEvent = processedEventCaptor.getValue();
        assertEquals("INV-001", processedEvent.getInvoiceNumber());
        assertEquals("THB", processedEvent.getCurrency());
        assertEquals("correlation-123", processedEvent.getCorrelationId());

        ArgumentCaptor<PdfGenerationRequestedEvent> pdfEventCaptor =
            ArgumentCaptor.forClass(PdfGenerationRequestedEvent.class);
        verify(eventPublisher).publishPdfGenerationRequested(pdfEventCaptor.capture());

        PdfGenerationRequestedEvent pdfEvent = pdfEventCaptor.getValue();
        assertEquals("INV-001", pdfEvent.getInvoiceNumber());
        assertEquals("<xml>test</xml>", pdfEvent.getXmlContent());
        assertEquals("correlation-123", pdfEvent.getCorrelationId());
    }

    @Test
    void testFindByIdValid() {
        // Given
        InvoiceId id = InvoiceId.generate();
        when(invoiceRepository.findById(any(InvoiceId.class))).thenReturn(Optional.of(validInvoice));

        // When
        Optional<ProcessedInvoice> result = service.findById(id.toString());

        // Then
        assertTrue(result.isPresent());
        assertEquals(validInvoice, result.get());
        verify(invoiceRepository).findById(any(InvoiceId.class));
    }

    @Test
    void testFindByIdInvalidFormat() {
        // Given
        String invalidId = "not-a-uuid";

        // When
        Optional<ProcessedInvoice> result = service.findById(invalidId);

        // Then
        assertFalse(result.isPresent());
        verify(invoiceRepository, never()).findById(any(InvoiceId.class));
    }

    @Test
    void testFindByIdNotFound() {
        // Given
        InvoiceId id = InvoiceId.generate();
        when(invoiceRepository.findById(any(InvoiceId.class))).thenReturn(Optional.empty());

        // When
        Optional<ProcessedInvoice> result = service.findById(id.toString());

        // Then
        assertFalse(result.isPresent());
        verify(invoiceRepository).findById(any(InvoiceId.class));
    }

    @Test
    void testFindByStatus() {
        // Given
        ProcessingStatus status = ProcessingStatus.COMPLETED;
        List<ProcessedInvoice> invoices = List.of(validInvoice);
        when(invoiceRepository.findByStatus(status)).thenReturn(invoices);

        // When
        List<ProcessedInvoice> result = service.findByStatus(status);

        // Then
        assertEquals(1, result.size());
        assertEquals(validInvoice, result.get(0));
        verify(invoiceRepository).findByStatus(status);
    }

    @Test
    void testFindByStatusEmpty() {
        // Given
        ProcessingStatus status = ProcessingStatus.FAILED;
        when(invoiceRepository.findByStatus(status)).thenReturn(List.of());

        // When
        List<ProcessedInvoice> result = service.findByStatus(status);

        // Then
        assertTrue(result.isEmpty());
        verify(invoiceRepository).findByStatus(status);
    }

    @Test
    void testCreateInvoiceDataJson() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"invoiceNumber\":\"INV-001\"}");

        // When
        service.processInvoiceReceived(validEvent);

        // Then
        ArgumentCaptor<PdfGenerationRequestedEvent> pdfEventCaptor =
            ArgumentCaptor.forClass(PdfGenerationRequestedEvent.class);
        verify(eventPublisher).publishPdfGenerationRequested(pdfEventCaptor.capture());

        PdfGenerationRequestedEvent pdfEvent = pdfEventCaptor.getValue();
        assertNotNull(pdfEvent.getInvoiceDataJson());
        assertEquals("{\"invoiceNumber\":\"INV-001\"}", pdfEvent.getInvoiceDataJson());
    }

    @Test
    void testCreateInvoiceDataJsonError() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON error"));

        // When
        service.processInvoiceReceived(validEvent);

        // Then - Should handle error and use empty JSON
        ArgumentCaptor<PdfGenerationRequestedEvent> pdfEventCaptor =
            ArgumentCaptor.forClass(PdfGenerationRequestedEvent.class);
        verify(eventPublisher).publishPdfGenerationRequested(pdfEventCaptor.capture());

        PdfGenerationRequestedEvent pdfEvent = pdfEventCaptor.getValue();
        assertEquals("{}", pdfEvent.getInvoiceDataJson());
    }

    @Test
    void testRepositorySaveCalledThreeTimes() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        service.processInvoiceReceived(validEvent);

        // Then - Should save 3 times: initial save, after completed, after PDF requested
        verify(invoiceRepository, times(3)).save(any(ProcessedInvoice.class));
    }

    @Test
    void testTransactionalBehavior() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When
        service.processInvoiceReceived(validEvent);

        // Then - Should not publish events if save fails
        verify(eventPublisher, never()).publishInvoiceProcessed(any());
        verify(eventPublisher, never()).publishPdfGenerationRequested(any());
    }
}
