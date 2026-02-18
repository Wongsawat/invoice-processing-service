package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.domain.event.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.domain.event.ProcessInvoiceCommand;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
import com.wpanther.invoice.processing.infrastructure.messaging.SagaReplyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles saga commands from orchestrator.
 * Delegates business logic to InvoiceProcessingService and sends replies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final InvoiceProcessingService processingService;
    private final SagaReplyPublisher sagaReplyPublisher;
    private final ProcessedInvoiceRepository invoiceRepository;

    /**
     * Handle a ProcessInvoiceCommand from saga orchestrator.
     * Processes invoice and sends a SUCCESS or FAILURE reply.
     */
    @Transactional
    public void handleProcessCommand(ProcessInvoiceCommand command) {
        log.info("Handling ProcessInvoiceCommand for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        try {
            processingService.processInvoiceForSaga(
                command.getDocumentId(),
                command.getXmlContent(),
                command.getCorrelationId()
            );

            sagaReplyPublisher.publishSuccess(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );

            log.info("Successfully processed invoice for saga {}", command.getSagaId());

        } catch (Exception e) {
            log.error("Failed to process invoice for saga {}: {}",
                command.getSagaId(), e.getMessage(), e);

            sagaReplyPublisher.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                e.getMessage()
            );
        }
    }

    /**
     * Handle a CompensateInvoiceCommand from saga orchestrator.
     * Hard deletes processed invoice and sends a COMPENSATED reply.
     */
    @Transactional
    public void handleCompensation(CompensateInvoiceCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        try {
            Optional<ProcessedInvoice> existing =
                invoiceRepository.findBySourceInvoiceId(command.getDocumentId());

            if (existing.isPresent()) {
                invoiceRepository.deleteById(existing.get().getId());
                log.info("Deleted ProcessedInvoice {} for compensation",
                    existing.get().getId());
            } else {
                log.info("No ProcessedInvoice found for document {} - already compensated or never processed",
                    command.getDocumentId());
            }

            sagaReplyPublisher.publishCompensated(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );

        } catch (Exception e) {
            log.error("Failed to compensate invoice for saga {}: {}",
                command.getSagaId(), e.getMessage(), e);

            sagaReplyPublisher.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                "Compensation failed: " + e.getMessage()
            );
        }
    }
}
