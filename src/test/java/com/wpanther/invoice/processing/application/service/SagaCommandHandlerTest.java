package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.SagaCommandHandler;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SagaCommandHandler
 * Tests that the handler correctly delegates to use cases.
 */
@ExtendWith(MockitoExtension.class)
class SagaCommandHandlerTest {

    @Mock
    private ProcessInvoiceUseCase processInvoiceUseCase;

    @Mock
    private CompensateInvoiceUseCase compensateInvoiceUseCase;

    @InjectMocks
    private SagaCommandHandler sagaCommandHandler;

    @BeforeEach
    void setUp() {
        // No setup needed - using @InjectMocks
    }

    @Test
    void shouldDelegateToProcessInvoiceUseCase() throws Exception {
        // Given
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        doNothing().when(processInvoiceUseCase).process(any(), any(), any(), any(), any());

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        verify(processInvoiceUseCase).process(
            eq("doc-001"),
            eq("<xml/>"),
            eq("saga-1"),
            eq(SagaStep.PROCESS_INVOICE),
            eq("corr-1")
        );
        verify(compensateInvoiceUseCase, never()).compensate(any(), any(), any(), any());
    }

    @Test
    void shouldDelegateToCompensateInvoiceUseCase() {
        // Given
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "process-invoice", "doc-001", "invoice"
        );
        doNothing().when(compensateInvoiceUseCase).compensate(any(), any(), any(), any());

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then
        verify(compensateInvoiceUseCase).compensate(
            eq("doc-001"),
            eq("saga-1"),
            eq(SagaStep.PROCESS_INVOICE),
            eq("corr-1")
        );
        verify(processInvoiceUseCase, never()).process(any(), any(), any(), any(), any());
    }

    @Test
    void shouldPropagateExceptionFromProcessUseCase() throws Exception {
        // Given
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        doThrow(new RuntimeException("Processing error"))
            .when(processInvoiceUseCase).process(any(), any(), any(), any(), any());

        // When/Then - exception propagates
        try {
            sagaCommandHandler.handleProcessCommand(command);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(processInvoiceUseCase).process(any(), any(), any(), any(), any());
    }

    @Test
    void shouldPropagateExceptionFromCompensateUseCase() {
        // Given
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "process-invoice", "doc-001", "invoice"
        );
        doThrow(new RuntimeException("Compensation error"))
            .when(compensateInvoiceUseCase).compensate(any(), any(), any(), any());

        // When/Then - exception propagates
        try {
            sagaCommandHandler.handleCompensation(command);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(compensateInvoiceUseCase).compensate(any(), any(), any(), any());
    }
}
