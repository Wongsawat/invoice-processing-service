package com.wpanther.taxinvoice.processing.application.service;

import com.wpanther.taxinvoice.processing.domain.event.CompensateTaxInvoiceCommand;
import com.wpanther.taxinvoice.processing.domain.event.ProcessTaxInvoiceCommand;
import com.wpanther.taxinvoice.processing.domain.model.ProcessedTaxInvoice;
import com.wpanther.taxinvoice.processing.domain.repository.ProcessedTaxInvoiceRepository;
import com.wpanther.taxinvoice.processing.infrastructure.messaging.SagaReplyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles saga commands from the orchestrator.
 * Delegates business logic to TaxInvoiceProcessingService and sends replies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final TaxInvoiceProcessingService processingService;
    private final SagaReplyPublisher sagaReplyPublisher;
    private final ProcessedTaxInvoiceRepository invoiceRepository;

    /**
     * Handle a ProcessTaxInvoiceCommand from the saga orchestrator.
     * Processes the tax invoice and sends a SUCCESS or FAILURE reply.
     */
    @Transactional
    public void handleProcessCommand(ProcessTaxInvoiceCommand command) {
        log.info("Handling ProcessTaxInvoiceCommand for saga {} document {}",
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

            log.info("Successfully processed tax invoice for saga {}", command.getSagaId());

        } catch (Exception e) {
            log.error("Failed to process tax invoice for saga {}: {}",
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
     * Handle a CompensateTaxInvoiceCommand from the saga orchestrator.
     * Hard deletes the processed tax invoice and sends a COMPENSATED reply.
     */
    @Transactional
    public void handleCompensation(CompensateTaxInvoiceCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        try {
            Optional<ProcessedTaxInvoice> existing =
                invoiceRepository.findBySourceInvoiceId(command.getDocumentId());

            if (existing.isPresent()) {
                invoiceRepository.deleteById(existing.get().getId());
                log.info("Deleted ProcessedTaxInvoice {} for compensation",
                    existing.get().getId());
            } else {
                log.info("No ProcessedTaxInvoice found for document {} - already compensated or never processed",
                    command.getDocumentId());
            }

            sagaReplyPublisher.publishCompensated(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );

        } catch (Exception e) {
            log.error("Failed to compensate tax invoice for saga {}: {}",
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
