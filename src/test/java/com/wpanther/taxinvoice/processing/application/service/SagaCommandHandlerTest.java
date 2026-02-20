package com.wpanther.taxinvoice.processing.application.service;

import com.wpanther.taxinvoice.processing.domain.event.CompensateTaxInvoiceCommand;
import com.wpanther.taxinvoice.processing.domain.event.ProcessTaxInvoiceCommand;
import com.wpanther.taxinvoice.processing.domain.model.*;
import com.wpanther.taxinvoice.processing.domain.repository.ProcessedTaxInvoiceRepository;
import com.wpanther.taxinvoice.processing.domain.service.TaxInvoiceParserService;
import com.wpanther.taxinvoice.processing.infrastructure.messaging.SagaReplyPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaCommandHandlerTest {

    @Mock
    private TaxInvoiceProcessingService processingService;

    @Mock
    private SagaReplyPublisher sagaReplyPublisher;

    @Mock
    private ProcessedTaxInvoiceRepository invoiceRepository;

    @InjectMocks
    private SagaCommandHandler handler;

    private ProcessTaxInvoiceCommand validCommand;
    private CompensateTaxInvoiceCommand compensateCommand;
    private ProcessedTaxInvoice validInvoice;

    @BeforeEach
    void setUp() {
        validCommand = new ProcessTaxInvoiceCommand(
            "saga-1", "process-tax-invoice", "corr-1",
            "doc-1", "<xml>test</xml>", "TV-001"
        );

        compensateCommand = new CompensateTaxInvoiceCommand(
            "saga-1", "COMPENSATE_process-tax-invoice", "corr-1",
            "process-tax-invoice", "doc-1", "tax-invoice"
        );

        Party seller = Party.of(
            "Seller", TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 St", "Bangkok", "10110", "TH")
        );
        Party buyer = Party.of(
            "Buyer", TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Rd", "Chiang Mai", "50000", "TH")
        );
        LineItem item = new LineItem("Service", 10, Money.of(1000.00, "THB"), new BigDecimal("7.00"));

        validInvoice = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("doc-1")
            .invoiceNumber("TV-001")
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
    void testHandleProcessCommandSuccess() throws Exception {
        when(processingService.processInvoiceForSaga(anyString(), anyString(), anyString()))
            .thenReturn(validInvoice);

        handler.handleProcessCommand(validCommand);

        verify(processingService).processInvoiceForSaga("doc-1", "<xml>test</xml>", "corr-1");
        verify(sagaReplyPublisher).publishSuccess("saga-1", "process-tax-invoice", "corr-1");
        verify(sagaReplyPublisher, never()).publishFailure(any(), any(), any(), any());
    }

    @Test
    void testHandleProcessCommandFailure() throws Exception {
        when(processingService.processInvoiceForSaga(anyString(), anyString(), anyString()))
            .thenThrow(new TaxInvoiceParserService.TaxInvoiceParsingException("Parse error"));

        handler.handleProcessCommand(validCommand);

        verify(sagaReplyPublisher).publishFailure("saga-1", "process-tax-invoice", "corr-1", "Parse error");
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any());
    }

    @Test
    void testHandleProcessCommandRuntimeError() throws Exception {
        when(processingService.processInvoiceForSaga(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("DB error"));

        handler.handleProcessCommand(validCommand);

        verify(sagaReplyPublisher).publishFailure("saga-1", "process-tax-invoice", "corr-1", "DB error");
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any());
    }

    @Test
    void testHandleCompensationFound() {
        when(invoiceRepository.findBySourceInvoiceId("doc-1")).thenReturn(Optional.of(validInvoice));

        handler.handleCompensation(compensateCommand);

        verify(invoiceRepository).deleteById(validInvoice.getId());
        verify(sagaReplyPublisher).publishCompensated("saga-1", "COMPENSATE_process-tax-invoice", "corr-1");
        verify(sagaReplyPublisher, never()).publishFailure(any(), any(), any(), any());
    }

    @Test
    void testHandleCompensationNotFound() {
        when(invoiceRepository.findBySourceInvoiceId("doc-1")).thenReturn(Optional.empty());

        handler.handleCompensation(compensateCommand);

        verify(invoiceRepository, never()).deleteById(any());
        verify(sagaReplyPublisher).publishCompensated("saga-1", "COMPENSATE_process-tax-invoice", "corr-1");
    }

    @Test
    void testHandleCompensationDeleteError() {
        when(invoiceRepository.findBySourceInvoiceId("doc-1")).thenReturn(Optional.of(validInvoice));
        doThrow(new RuntimeException("Delete failed")).when(invoiceRepository).deleteById(any());

        handler.handleCompensation(compensateCommand);

        verify(sagaReplyPublisher).publishFailure(
            eq("saga-1"), eq("COMPENSATE_process-tax-invoice"), eq("corr-1"),
            contains("Compensation failed")
        );
        verify(sagaReplyPublisher, never()).publishCompensated(any(), any(), any());
    }
}
