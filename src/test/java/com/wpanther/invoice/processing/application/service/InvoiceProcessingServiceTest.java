package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.out.InvoiceEventPublishingPort;
import com.wpanther.invoice.processing.application.port.out.SagaReplyPort;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.domain.model.*;
import com.wpanther.invoice.processing.domain.port.out.InvoiceParserPort;
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
import com.wpanther.saga.domain.enums.SagaStep;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvoiceProcessingService
 */
@ExtendWith(MockitoExtension.class)
class InvoiceProcessingServiceTest {

    @Mock
    private ProcessedInvoiceRepository invoiceRepository;

    @Mock
    private InvoiceParserPort parserService;

    @Mock
    private SagaReplyPort sagaReplyPort;

    @Mock
    private InvoiceEventPublishingPort eventPublisher;

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
    void testProcessSuccess() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);

        // When
        service.process("intake-123", "<xml>test</xml>", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService).parse("<xml>test</xml>", "intake-123");
        verify(invoiceRepository, times(2)).save(any(ProcessedInvoice.class));
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        ArgumentCaptor<InvoiceProcessedDomainEvent> eventCaptor = ArgumentCaptor.forClass(InvoiceProcessedDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        InvoiceProcessedDomainEvent event = eventCaptor.getValue();
        assertEquals("INV-001", event.invoiceNumber());
        assertEquals("THB", event.total().currency());
        assertEquals("correlation-123", event.correlationId());
    }

    @Test
    void testProcessIdempotency() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.of(validInvoice));

        // When
        service.process("intake-123", "<xml>test</xml>", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(parserService, never()).parse(anyString(), anyString());
        verify(invoiceRepository, never()).save(any(ProcessedInvoice.class));
        verify(eventPublisher, never()).publish(any());
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");
    }

    @Test
    void testProcessPublishesCorrectEvent() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);

        // When
        service.process("intake-123", "<xml>test</xml>", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then
        ArgumentCaptor<InvoiceProcessedDomainEvent> eventCaptor = ArgumentCaptor.forClass(InvoiceProcessedDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        InvoiceProcessedDomainEvent event = eventCaptor.getValue();
        assertEquals("INV-001", event.invoiceNumber());
        assertEquals("THB", event.total().currency());
        assertEquals("correlation-123", event.correlationId());
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
    void testProcessSavesTwice() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class))).thenReturn(validInvoice);

        // When
        service.process("intake-123", "<xml>test</xml>", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then - Should save twice: PROCESSING state, then COMPLETED state
        verify(invoiceRepository, times(2)).save(any(ProcessedInvoice.class));
    }

    @Test
    void testProcessHandlesRaceCondition() throws Exception {
        // Given - simulating race condition:
        // 1. First check returns empty (no existing invoice)
        // 2. Save throws DataIntegrityViolationException (concurrent insert)
        // The new implementation treats this as idempotent success
        when(invoiceRepository.findBySourceInvoiceId("intake-123"))
            .thenReturn(Optional.empty());  // First call - not found

        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // When
        service.process("intake-123", "<xml>test</xml>", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then - should succeed (idempotent success)
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(invoiceRepository).save(any(ProcessedInvoice.class));
        // Event should NOT be published in race condition case
        verify(eventPublisher, never()).publish(any());
        // Should publish success for idempotent case
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");
    }

    @Test
    void testProcessPublishesFailureWhenDataIntegrityViolationWithNoInvoice() throws Exception {
        // Given - DataIntegrityViolationException but invoice still not found
        // New implementation publishes success for idempotency
        when(invoiceRepository.findBySourceInvoiceId("intake-123"))
            .thenReturn(Optional.empty());

        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // When
        service.process("intake-123", "<xml>test</xml>", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then - new implementation treats this as idempotent success
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");
    }

    @Test
    void testProcessPublishesFailureWhenParsingThrows() throws Exception {
        // Given
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parse(anyString(), anyString())).thenThrow(new RuntimeException("Parse error"));

        // When — process() commits FAILURE reply then throws InvoiceProcessingException
        assertThrows(ProcessInvoiceUseCase.InvoiceProcessingException.class, () ->
            service.process("intake-123", "<xml>test</xml>", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123")
        );

        // Then — FAILURE reply was published before the exception was thrown
        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_INVOICE),
            eq("correlation-123"), anyString());
        verify(sagaReplyPort, never()).publishSuccess(any(), any(), any());
    }

    @Test
    void testCompensateDeletesInvoice() {
        // Given
        when(invoiceRepository.findBySourceInvoiceId("intake-123")).thenReturn(Optional.of(validInvoice));

        // When
        service.compensate("intake-123", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(invoiceRepository).deleteById(validInvoice.getId());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");
    }

    @Test
    void testCompensateWhenInvoiceNotFound() {
        // Given
        when(invoiceRepository.findBySourceInvoiceId("intake-123")).thenReturn(Optional.empty());

        // When
        service.compensate("intake-123", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");

        // Then
        verify(invoiceRepository).findBySourceInvoiceId("intake-123");
        verify(invoiceRepository, never()).deleteById(any());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");
    }

    @Test
    void testCompensatePublishesFailureAndThrowsWhenExceptionOccurs() {
        // Given — deleteById throws a runtime exception
        when(invoiceRepository.findBySourceInvoiceId("intake-123")).thenReturn(Optional.of(validInvoice));
        doThrow(new RuntimeException("DB error")).when(invoiceRepository).deleteById(any());

        // When — compensate() commits FAILURE reply then throws InvoiceCompensationException
        assertThrows(CompensateInvoiceUseCase.InvoiceCompensationException.class, () ->
            service.compensate("intake-123", "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123")
        );

        // Then — FAILURE reply was published before the exception was thrown
        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_INVOICE),
            eq("correlation-123"), anyString());
        verify(sagaReplyPort, never()).publishCompensated(any(), any(), any());
    }
}
