package com.wpanther.taxinvoice.processing.application.service;

import com.wpanther.taxinvoice.processing.domain.event.TaxInvoiceProcessedEvent;
import com.wpanther.taxinvoice.processing.domain.model.*;
import com.wpanther.taxinvoice.processing.domain.repository.ProcessedTaxInvoiceRepository;
import com.wpanther.taxinvoice.processing.domain.service.TaxInvoiceParserService;
import com.wpanther.taxinvoice.processing.infrastructure.messaging.EventPublisher;
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
 * Unit tests for TaxInvoiceProcessingService (saga version)
 */
@ExtendWith(MockitoExtension.class)
class TaxInvoiceProcessingServiceTest {

    @Mock
    private ProcessedTaxInvoiceRepository invoiceRepository;

    @Mock
    private TaxInvoiceParserService parserService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private TaxInvoiceProcessingService service;

    private ProcessedTaxInvoice validInvoice;

    @BeforeEach
    void setUp() {
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

        validInvoice = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("TXN-001")
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
    void testProcessInvoiceForSagaSuccess() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedTaxInvoice.class))).thenReturn(validInvoice);

        // When
        ProcessedTaxInvoice result = service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then
        assertNotNull(result);
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService).parseInvoice("<xml>test</xml>", "intake-123");
        verify(invoiceRepository, times(2)).save(any(ProcessedTaxInvoice.class));
        verify(eventPublisher).publishTaxInvoiceProcessed(any(TaxInvoiceProcessedEvent.class));
    }

    @Test
    void testProcessInvoiceForSagaAlreadyProcessed() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.of(validInvoice));

        // When
        ProcessedTaxInvoice result = service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then
        assertNotNull(result);
        assertEquals(validInvoice, result);
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService, never()).parseInvoice(anyString(), anyString());
        verify(invoiceRepository, never()).save(any(ProcessedTaxInvoice.class));
        verify(eventPublisher, never()).publishTaxInvoiceProcessed(any());
    }

    @Test
    void testProcessInvoiceForSagaParsingError() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString()))
            .thenThrow(new TaxInvoiceParserService.TaxInvoiceParsingException("Parse error"));

        // When / Then
        assertThrows(TaxInvoiceParserService.TaxInvoiceParsingException.class,
            () -> service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123"));

        verify(parserService).parseInvoice("<xml>test</xml>", "intake-123");
        verify(invoiceRepository, never()).save(any(ProcessedTaxInvoice.class));
        verify(eventPublisher, never()).publishTaxInvoiceProcessed(any());
    }

    @Test
    void testProcessInvoiceForSagaPublishesCorrectEvent() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedTaxInvoice.class))).thenReturn(validInvoice);

        // When
        service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then
        ArgumentCaptor<TaxInvoiceProcessedEvent> eventCaptor =
            ArgumentCaptor.forClass(TaxInvoiceProcessedEvent.class);
        verify(eventPublisher).publishTaxInvoiceProcessed(eventCaptor.capture());

        TaxInvoiceProcessedEvent processedEvent = eventCaptor.getValue();
        assertEquals("TXN-001", processedEvent.getInvoiceNumber());
        assertEquals("THB", processedEvent.getCurrency());
        assertEquals("correlation-123", processedEvent.getCorrelationId());
    }

    @Test
    void testProcessInvoiceForSagaSavesTwice() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedTaxInvoice.class))).thenReturn(validInvoice);

        // When
        service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then - Should save twice: PROCESSING and COMPLETED
        verify(invoiceRepository, times(2)).save(any(ProcessedTaxInvoice.class));
    }

    @Test
    void testProcessInvoiceForSagaDatabaseError() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedTaxInvoice.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When / Then
        assertThrows(RuntimeException.class,
            () -> service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123"));

        verify(eventPublisher, never()).publishTaxInvoiceProcessed(any());
    }

    @Test
    void testProcessInvoiceForSagaReturnsProcessedInvoice() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedTaxInvoice.class))).thenReturn(validInvoice);

        // When
        ProcessedTaxInvoice result = service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "corr-1");

        // Then
        assertNotNull(result);
        assertEquals("TXN-001", result.getInvoiceNumber());
    }

    @Test
    void testFindByIdValid() {
        // Given
        TaxInvoiceId id = TaxInvoiceId.generate();
        when(invoiceRepository.findById(any(TaxInvoiceId.class))).thenReturn(Optional.of(validInvoice));

        // When
        Optional<ProcessedTaxInvoice> result = service.findById(id.toString());

        // Then
        assertTrue(result.isPresent());
        assertEquals(validInvoice, result.get());
        verify(invoiceRepository).findById(any(TaxInvoiceId.class));
    }

    @Test
    void testFindByIdInvalidFormat() {
        // Given
        String invalidId = "not-a-uuid";

        // When
        Optional<ProcessedTaxInvoice> result = service.findById(invalidId);

        // Then
        assertFalse(result.isPresent());
        verify(invoiceRepository, never()).findById(any(TaxInvoiceId.class));
    }

    @Test
    void testFindByIdNotFound() {
        // Given
        TaxInvoiceId id = TaxInvoiceId.generate();
        when(invoiceRepository.findById(any(TaxInvoiceId.class))).thenReturn(Optional.empty());

        // When
        Optional<ProcessedTaxInvoice> result = service.findById(id.toString());

        // Then
        assertFalse(result.isPresent());
        verify(invoiceRepository).findById(any(TaxInvoiceId.class));
    }

    @Test
    void testFindByStatus() {
        // Given
        ProcessingStatus status = ProcessingStatus.COMPLETED;
        List<ProcessedTaxInvoice> invoices = List.of(validInvoice);
        when(invoiceRepository.findByStatus(status)).thenReturn(invoices);

        // When
        List<ProcessedTaxInvoice> result = service.findByStatus(status);

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
        List<ProcessedTaxInvoice> result = service.findByStatus(status);

        // Then
        assertTrue(result.isEmpty());
        verify(invoiceRepository).findByStatus(status);
    }
}
