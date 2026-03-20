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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.sql.SQLException;
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

    @Mock
    private PlatformTransactionManager transactionManager;

    private InvoiceProcessingService service;

    private ProcessedInvoice validInvoice;

    @BeforeEach
    void setUp() {
        service = new InvoiceProcessingService(
            invoiceRepository,
            parserService,
            sagaReplyPort,
            eventPublisher,
            transactionManager,
            new SimpleMeterRegistry()
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
        assertEquals("saga-1", event.sagaId());
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
        assertEquals("saga-1", event.sagaId());
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

    // ---- Race-condition (DuplicateKeyException on source_invoice_id) tests ----

    /**
     * Race condition resolved as success: DuplicateKeyException on source_invoice_id, and the
     * REQUIRES_NEW re-check finds the document already committed by the concurrent thread.
     * publishSuccess must be called; InvoiceProcessingException must still propagate so that
     * Spring does not try to commit the ROLLBACK_ONLY outer transaction.
     */
    @Test
    void testProcessHandlesRaceConditionResolvesAsSuccess() throws Exception {
        // Given — race condition: idempotency check passes, then insert conflicts
        // on idx_source_invoice_id, and re-check finds the winning thread's record.
        // SQLException with SQLState "23505" is required by isSourceInvoiceIdViolation().
        SQLException sqlCause = new SQLException(
            "ERROR: duplicate key value violates unique constraint \"idx_source_invoice_id\"", "23505");
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(invoiceRepository.findBySourceInvoiceId(anyString()))
            .thenReturn(Optional.empty())           // 1st call: idempotency check — not yet
            .thenReturn(Optional.of(validInvoice)); // 2nd call: REQUIRES_NEW re-check — found
        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DuplicateKeyException("duplicate key", sqlCause));

        // When / Then — exception still propagates (prevents Spring UnexpectedRollbackException)
        ProcessInvoiceUseCase.InvoiceProcessingException ex =
            assertThrows(ProcessInvoiceUseCase.InvoiceProcessingException.class,
                () -> service.process("intake-123", "<xml>test</xml>",
                                      "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123"));
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());

        // SUCCESS reply published because the document was found on re-check
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_INVOICE, "correlation-123");
        verify(sagaReplyPort, never()).publishFailure(any(), any(), any(), any());

        // Domain event never published by this thread (the winning thread already did so)
        verify(eventPublisher, never()).publish(any());
    }

    /**
     * Race condition "ghost duplicate": DuplicateKeyException on source_invoice_id, but the
     * REQUIRES_NEW re-check finds no record (the winning thread rolled back or never committed).
     * publishFailure must be called; InvoiceProcessingException must propagate.
     */
    @Test
    void testProcessHandlesRaceConditionGhostDuplicate() throws Exception {
        // Given — idempotency check passes, insert conflicts, re-check finds nothing
        SQLException sqlCause = new SQLException(
            "ERROR: duplicate key value violates unique constraint \"idx_source_invoice_id\"", "23505");
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(invoiceRepository.findBySourceInvoiceId(anyString()))
            .thenReturn(Optional.empty());  // both idempotency check and re-check return empty
        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DuplicateKeyException("duplicate key", sqlCause));

        // When / Then — InvoiceProcessingException propagates with original cause
        ProcessInvoiceUseCase.InvoiceProcessingException ex =
            assertThrows(ProcessInvoiceUseCase.InvoiceProcessingException.class,
                () -> service.process("intake-123", "<xml>test</xml>",
                                      "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123"));
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());

        // FAILURE reply published because re-check found no record
        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_INVOICE),
            eq("correlation-123"), anyString());
        verify(sagaReplyPort, never()).publishSuccess(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    /**
     * A plain DataIntegrityViolationException (value-too-long, check-constraint, etc.)
     * is NOT a DuplicateKeyException, so it must skip the race-condition re-check entirely
     * and publish FAILURE immediately.
     */
    @Test
    void testProcessNonDuplicateKeyConstraintViolation() throws Exception {
        // Given — data-too-long violation, not a duplicate key
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DataIntegrityViolationException("value too long for type character varying(500)"));

        // When / Then — InvoiceProcessingException thrown immediately
        ProcessInvoiceUseCase.InvoiceProcessingException ex =
            assertThrows(ProcessInvoiceUseCase.InvoiceProcessingException.class,
                () -> service.process("intake-123", "<xml>test</xml>",
                                      "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123"));
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("Constraint violation"),
            "Exception message should say 'Constraint violation'; got: " + ex.getMessage());

        // FAILURE reply published; re-check must NOT happen
        verify(sagaReplyPort).publishFailure(
            eq("saga-1"), eq(SagaStep.PROCESS_INVOICE), eq("correlation-123"),
            contains("Constraint violation"));
        // Re-check must NOT happen — transactionManager.getTransaction never called
        verify(transactionManager, never()).getTransaction(any());
        verify(eventPublisher, never()).publish(any());
    }

    /**
     * A DuplicateKeyException whose cause message does NOT contain the source_invoice_id
     * index name (e.g. duplicate invoice_number from a different document) must be treated
     * as a plain constraint violation — no REQUIRES_NEW re-check.
     */
    @Test
    void testProcessDuplicateKeyOnNonIdempotentConstraint() throws Exception {
        // Given — invoice_number duplicate: constraint name does NOT contain idx_source_invoice_id
        SQLException sqlCause = new SQLException(
            "ERROR: duplicate key value violates unique constraint \"idx_invoice_number_unique\"", "23505");
        when(invoiceRepository.findBySourceInvoiceId(anyString())).thenReturn(Optional.empty());
        when(parserService.parse(anyString(), anyString())).thenReturn(validInvoice);
        when(invoiceRepository.save(any(ProcessedInvoice.class)))
            .thenThrow(new DuplicateKeyException("duplicate key", sqlCause));

        // When / Then — InvoiceProcessingException thrown immediately
        ProcessInvoiceUseCase.InvoiceProcessingException ex =
            assertThrows(ProcessInvoiceUseCase.InvoiceProcessingException.class,
                () -> service.process("intake-123", "<xml>test</xml>",
                                      "saga-1", SagaStep.PROCESS_INVOICE, "correlation-123"));
        assertInstanceOf(DuplicateKeyException.class, ex.getCause());

        // FAILURE reply published with "Constraint violation:" prefix
        verify(sagaReplyPort).publishFailure(
            eq("saga-1"), eq(SagaStep.PROCESS_INVOICE), eq("correlation-123"),
            contains("Constraint violation"));
        // Re-check must NOT happen
        verify(transactionManager, never()).getTransaction(any());
        verify(eventPublisher, never()).publish(any());
    }

    // ---- Compensation tests ----

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
