package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saga command handler - driving adapter that receives Kafka messages and calls use cases.
 * Routes ProcessInvoiceCommand → ProcessInvoiceUseCase.process()
 * Routes CompensateInvoiceCommand → CompensateInvoiceUseCase.compensate()
 * The use case handles reply publishing internally.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ProcessInvoiceUseCase processInvoiceUseCase;
    private final CompensateInvoiceUseCase compensateInvoiceUseCase;

    /**
     * Handle a ProcessInvoiceCommand from saga orchestrator.
     * Delegates to use case which handles business logic and reply publishing.
     */
    public void handleProcessCommand(ProcessInvoiceCommand command) {
        log.info("Handling ProcessInvoiceCommand for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        processInvoiceUseCase.process(
            command.getDocumentId(),
            command.getXmlContent(),
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId()
        );
    }

    /**
     * Handle a CompensateInvoiceCommand from saga orchestrator.
     * Delegates to use case which handles business logic and reply publishing.
     */
    public void handleCompensation(CompensateInvoiceCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        compensateInvoiceUseCase.compensate(
            command.getDocumentId(),
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId()
        );
    }
}
