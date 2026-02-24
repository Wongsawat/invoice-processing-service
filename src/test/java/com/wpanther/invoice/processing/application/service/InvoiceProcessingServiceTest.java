package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.invoice.processing.domain.model.*;
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
import com.wpanther.invoice.processing.domain.service.InvoiceParserService;
import com.wpanther.invoice.processing.infrastructure.messaging.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

    @InjectMocks
    private InvoiceProcessingService service;

    private ProcessedInvoice validInvoice;

    @BeforeEach
    void setUp() {
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
    void testProcessInvoiceForSagaSuccess() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);

        // When
        ProcessedInvoice result = service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then
        assertNotNull(result);
        assertEquals("INV-001", result.getInvoiceNumber());
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService).parseInvoice("<xml>test</xml>", "intake-123");
        verify(invoiceRepository, times(2)).save(any(ProcessedInvoice.class));
        verify(eventPublisher).publishInvoiceProcessed(any(InvoiceProcessedEvent.class));
    }

    @Test
    void testProcessInvoiceForSagaIdempotency() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.of(validInvoice));

        // When
        ProcessedInvoice result = service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then
        assertNotNull(result);
        assertEquals(validInvoice, result);
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService, never()).parseInvoice(anyString(), anyString());
        verify(invoiceRepository, never()).save(any(ProcessedInvoice.class));
        verify(eventPublisher, never()).publishInvoiceProcessed(any());
    }

    @Test
    void testProcessInvoiceForSagaPublishesCorrectEvent() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);

        // When
        service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then
        ArgumentCaptor<InvoiceProcessedEvent> eventCaptor = ArgumentCaptor.forClass(InvoiceProcessedEvent.class);
        verify(eventPublisher).publishInvoiceProcessed(eventCaptor.capture());

        InvoiceProcessedEvent event = eventCaptor.getValue();
        assertEquals("INV-001", event.getInvoiceNumber());
        assertEquals("THB", event.getCurrency());
        assertEquals("correlation-123", event.getCorrelationId());
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
    void testProcessInvoiceForSagaSavesTwice() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);

        // When
        service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then - Should save twice: PROCESSING state, then COMPLETED state
        verify(invoiceRepository, times(2)).save(any(ProcessedInvoice.class));
    }

    @Test
    void testProcessInvoiceForSagaHandlesRaceCondition() throws Exception {
        // Given - simulating race condition:
        // 1. First check returns empty (no existing invoice)
        // 2. Save throws DataIntegrityViolationException (concurrent insert)
        // 3. Second fetch returns the existing invoice
        when(invoiceRepository.findBySourceInvoiceId("intake-123"))
            .thenReturn(Optional.empty())  // First call - not found
            .thenReturn(Optional.of(validInvoice));  // Second call - found after race condition

        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // When
        ProcessedInvoice result = service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123");

        // Then - should return the existing invoice
        assertNotNull(result);
        assertEquals("INV-001", result.getInvoiceNumber());
        verify(invoiceRepository, times(2)).findBySourceInvoiceId("intake-123");
        verify(invoiceRepository).save(any(ProcessedInvoice.class));
        // Event should NOT be published in race condition case (invoice already exists)
        verify(eventPublisher, never()).publishInvoiceProcessed(any());
    }

    @Test
    void testProcessInvoiceForSagaRaceConditionMissingInvoiceThrowsException() throws Exception {
        // Given - race condition but invoice not found after constraint violation (should not happen)
        when(invoiceRepository.findBySourceInvoiceId("intake-123"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.empty());  // Still not found after race condition

        when(parserService.parseInvoice(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // When & Then - should throw IllegalStateException
        assertThrows(IllegalStateException.class, () ->
            service.processInvoiceForSaga("intake-123", "<xml>test</xml>", "correlation-123")
        );
    }
}
