package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void shouldDelegateToCompensateInvoiceUseCase() throws Exception {
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
    void shouldSwallowInvoiceProcessingExceptionFromProcessUseCase() throws Exception {
        // Given — use case committed FAILURE reply and throws InvoiceProcessingException
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        doThrow(new ProcessInvoiceUseCase.InvoiceProcessingException("Parse failed"))
            .when(processInvoiceUseCase).process(any(), any(), any(), any(), any());

        // When/Then — handler catches it and returns normally; Kafka offset is committed
        sagaCommandHandler.handleProcessCommand(command);

        verify(processInvoiceUseCase).process(any(), any(), any(), any(), any());
    }

    @Test
    void shouldPropagateUnexpectedExceptionFromProcessUseCase() throws Exception {
        // Given — unexpected runtime exception (e.g., outbox write failed before reply committed)
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        doThrow(new RuntimeException("Unexpected DB outage"))
            .when(processInvoiceUseCase).process(any(), any(), any(), any(), any());

        // When/Then — propagates to Camel DLC for retry
        try {
            sagaCommandHandler.handleProcessCommand(command);
        } catch (RuntimeException e) {
            // Expected — Camel DLC retries the message
        }

        verify(processInvoiceUseCase).process(any(), any(), any(), any(), any());
    }

    @Test
    void shouldSwallowInvoiceCompensationExceptionFromCompensateUseCase() {
        // Given — use case committed FAILURE reply (via REQUIRES_NEW) and then threw.
        // The FAILURE reply is already in the outbox; the orchestrator will handle retry.
        // Propagating here would cause Camel to retry, sending a COMPENSATED reply that
        // arrives at the orchestrator after the FAILURE reply, crashing its state machine
        // when it tries to advance a COMPENSATING-state saga to the next forward step.
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "process-invoice", "doc-001", "invoice"
        );
        doThrow(new CompensateInvoiceUseCase.InvoiceCompensationException(
                "Compensation error", new RuntimeException("DB error")))
            .when(compensateInvoiceUseCase).compensate(any(), any(), any(), any());

        // When/Then — handler catches it and returns normally; Kafka offset is committed,
        // no Camel retry, no conflicting COMPENSATED reply to the orchestrator.
        assertDoesNotThrow(() -> sagaCommandHandler.handleCompensation(command));

        verify(compensateInvoiceUseCase).compensate(any(), any(), any(), any());
    }

    @Test
    void shouldPropagateUnexpectedExceptionFromCompensateUseCase() {
        // Given — unexpected exception (e.g., DB outage prevented publishFailure from committing).
        // No FAILURE reply was sent; Camel must retry so the orchestrator eventually gets a reply.
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "process-invoice", "doc-001", "invoice"
        );
        doThrow(new RuntimeException("Unexpected DB outage"))
            .when(compensateInvoiceUseCase).compensate(any(), any(), any(), any());

        // When/Then — propagates to Camel DLC for retry
        assertThrows(RuntimeException.class,
            () -> sagaCommandHandler.handleCompensation(command));

        verify(compensateInvoiceUseCase).compensate(any(), any(), any(), any());
    }
}
