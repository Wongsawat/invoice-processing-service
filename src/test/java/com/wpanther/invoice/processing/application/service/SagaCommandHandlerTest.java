package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.domain.event.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.domain.event.ProcessInvoiceCommand;
import com.wpanther.invoice.processing.domain.model.Address;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.LineItem;
import com.wpanther.invoice.processing.domain.model.Money;
import com.wpanther.invoice.processing.domain.model.Party;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.TaxIdentifier;
import com.wpanther.invoice.processing.domain.port.SagaReplyPort;
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SagaCommandHandler
 */
@ExtendWith(MockitoExtension.class)
class SagaCommandHandlerTest {

    @Mock
    private InvoiceProcessingService processingService;

    @Mock
    private SagaReplyPort sagaReplyPort;

    @Mock
    private ProcessedInvoiceRepository invoiceRepository;

    @InjectMocks
    private SagaCommandHandler sagaCommandHandler;

    private ProcessedInvoice testInvoice;

    @BeforeEach
    void setUp() {
        Party seller = Party.of(
            "Test Seller",
            TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 Street", "Bangkok", "10110", "TH")
        );
        Party buyer = Party.of(
            "Test Buyer",
            TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Road", "Chiang Mai", "50000", "TH")
        );
        LineItem item = new LineItem(
            "Test Service", 1, Money.of(1000.00, "THB"), new BigDecimal("7.00")
        );
        testInvoice = ProcessedInvoice.builder()
            .id(InvoiceId.generate())
            .sourceInvoiceId("doc-001")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(seller)
            .buyer(buyer)
            .addItem(item)
            .currency("THB")
            .originalXml("<xml/>")
            .build();
    }

    @Test
    void shouldPublishSuccessWhenProcessingSucceeds() throws Exception {
        // Given
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", "process-invoice", "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        when(processingService.processInvoiceForSaga(anyString(), anyString(), anyString()))
            .thenReturn(testInvoice);

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        verify(sagaReplyPort).publishSuccess("saga-1", "process-invoice", "corr-1");
        verify(sagaReplyPort, never()).publishFailure(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldPublishFailureWhenProcessingThrows() throws Exception {
        // Given
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", "process-invoice", "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        when(processingService.processInvoiceForSaga(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Parse error"));

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        verify(sagaReplyPort).publishFailure(
            eq("saga-1"), eq("process-invoice"), eq("corr-1"), contains("Parse error"));
        verify(sagaReplyPort, never()).publishSuccess(anyString(), anyString(), anyString());
    }

    @Test
    void shouldPublishFailureWhenPublishSuccessThrows() throws Exception {
        // Given
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", "process-invoice", "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        when(processingService.processInvoiceForSaga(anyString(), anyString(), anyString()))
            .thenReturn(testInvoice);
        doThrow(new RuntimeException("Outbox failure"))
            .when(sagaReplyPort).publishSuccess(anyString(), anyString(), anyString());

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        verify(sagaReplyPort).publishFailure(
            eq("saga-1"), eq("process-invoice"), eq("corr-1"), contains("Outbox failure"));
    }

    @Test
    void shouldPublishCompensatedWhenInvoiceFound() {
        // Given
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-1", "COMPENSATE_process-invoice", "corr-1", "process-invoice", "doc-001", "invoice"
        );
        when(invoiceRepository.findBySourceInvoiceId("doc-001")).thenReturn(Optional.of(testInvoice));

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then
        verify(invoiceRepository).deleteById(testInvoice.getId());
        verify(sagaReplyPort).publishCompensated("saga-1", "COMPENSATE_process-invoice", "corr-1");
    }

    @Test
    void shouldPublishCompensatedWhenInvoiceNotFound() {
        // Given
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-1", "COMPENSATE_process-invoice", "corr-1", "process-invoice", "doc-001", "invoice"
        );
        when(invoiceRepository.findBySourceInvoiceId("doc-001")).thenReturn(Optional.empty());

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then
        verify(invoiceRepository, never()).deleteById(any());
        verify(sagaReplyPort).publishCompensated("saga-1", "COMPENSATE_process-invoice", "corr-1");
    }

    @Test
    void shouldPublishFailureWhenCompensationThrows() {
        // Given
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-1", "COMPENSATE_process-invoice", "corr-1", "process-invoice", "doc-001", "invoice"
        );
        when(invoiceRepository.findBySourceInvoiceId("doc-001")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Outbox failure"))
            .when(sagaReplyPort).publishCompensated(anyString(), anyString(), anyString());

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then
        verify(sagaReplyPort).publishFailure(
            eq("saga-1"), eq("COMPENSATE_process-invoice"), eq("corr-1"),
            contains("Compensation failed"));
    }

    @Test
    void shouldSwallowPublishFailureException_inHandleProcessCommand() throws Exception {
        // Given: processing fails AND publishFailure itself also fails (double-fault)
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-double-fault", "process-invoice", "corr-double", "doc-fault", "<xml/>", "INV-FAULT"
        );
        when(processingService.processInvoiceForSaga(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Parse error"));
        doThrow(new RuntimeException("Outbox unavailable"))
            .when(sagaReplyPort).publishFailure(anyString(), anyString(), anyString(), anyString());

        // When/Then: inner catch should swallow the publishFailure exception - no re-throw
        assertDoesNotThrow(() -> sagaCommandHandler.handleProcessCommand(command));
    }

    @Test
    void shouldSwallowPublishFailureException_inHandleCompensation() {
        // Given: publishCompensated fails AND publishFailure also fails (double-fault in compensation)
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-comp-double", "COMPENSATE_process-invoice", "corr-comp-double",
            "process-invoice", "doc-comp-fault", "invoice"
        );
        when(invoiceRepository.findBySourceInvoiceId("doc-comp-fault")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Compensated publish failed"))
            .when(sagaReplyPort).publishCompensated(anyString(), anyString(), anyString());
        doThrow(new RuntimeException("Failure publish also failed"))
            .when(sagaReplyPort).publishFailure(anyString(), anyString(), anyString(), anyString());

        // When/Then: inner catch should swallow the publishFailure exception - no re-throw
        assertDoesNotThrow(() -> sagaCommandHandler.handleCompensation(command));
    }
}
