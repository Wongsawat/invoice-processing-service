package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.out.InvoiceEventPublishingPort;
import com.wpanther.invoice.processing.application.port.out.SagaReplyPort;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.ProcessingStatus;
import com.wpanther.invoice.processing.domain.port.out.InvoiceParserPort;
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service implementing both use case inbound ports.
 * Coordinates domain logic and all four outbound ports.
 * Zero imports from infrastructure — dependency rule enforced by package structure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceProcessingService
        implements ProcessInvoiceUseCase, CompensateInvoiceUseCase {

    private final ProcessedInvoiceRepository invoiceRepository;
    private final InvoiceParserPort parserPort;
    private final SagaReplyPort sagaReplyPort;
    private final InvoiceEventPublishingPort eventPublishingPort;

    @Override
    @Transactional
    public void process(String documentId, String xmlContent,
                        String sagaId, SagaStep sagaStep, String correlationId)
            throws InvoiceProcessingException {
        log.info("Processing invoice for saga={} document={}", sagaId, documentId);
        try {
            if (invoiceRepository.findBySourceInvoiceId(documentId).isPresent()) {
                log.warn("Invoice already processed for document={}, replying SUCCESS", documentId);
                sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                return;
            }

            ProcessedInvoice invoice = parserPort.parse(xmlContent, documentId);

            invoice.startProcessing();
            invoiceRepository.save(invoice);

            invoice.markCompleted(correlationId);
            invoiceRepository.save(invoice);

            invoice.domainEvents().forEach(e -> {
                if (e instanceof InvoiceProcessedDomainEvent domainEvent) {
                    eventPublishingPort.publish(domainEvent);
                }
            });
            invoice.clearDomainEvents();

            sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
            log.info("Successfully processed invoice={} for saga={}", invoice.getInvoiceNumber(), sagaId);

        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted same invoice between check and save.
            // Treat as idempotent success — do not fail the saga.
            log.warn("Duplicate invoice for document={}, treating as idempotent success", documentId);
            sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
        } catch (Exception e) {
            log.error("Failed to process invoice for saga={} document={}: {}",
                sagaId, documentId, e.getMessage(), e);
            // publishFailure uses REQUIRES_NEW — commits independently even if the outer
            // transaction is ROLLBACK_ONLY (e.g. after a DB error from save above).
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                "Processing error for document " + documentId + ": " + e);
            // Throw so the caller (SagaCommandHandler) knows the FAILURE reply was committed
            // and can return normally to Camel, committing the Kafka offset without retrying.
            throw new InvoiceProcessingException(
                "Failed to process invoice " + documentId + ": " + e, e);
        }
    }

    @Override
    @Transactional
    public void compensate(String documentId, String sagaId,
                           SagaStep sagaStep, String correlationId) {
        log.info("Compensating invoice for saga={} document={}", sagaId, documentId);

        try {
            invoiceRepository.findBySourceInvoiceId(documentId)
                .ifPresentOrElse(
                    invoice -> {
                        invoiceRepository.deleteById(invoice.getId());
                        log.info("Deleted ProcessedInvoice id={} for compensation", invoice.getId());
                    },
                    () -> log.info("No invoice found for document={} — already compensated or never processed",
                        documentId)
                );

            sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
        } catch (Exception e) {
            log.error("Failed to compensate invoice for saga={}: {}", sagaId, e.toString(), e);
            // publishFailure uses REQUIRES_NEW — commits in its own transaction even though
            // the outer @Transactional is rolling back.
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                "Compensation failed: " + e);
            // Rethrow so Camel receives a clean exception and triggers DLC retry.
            // deleteById is idempotent (no-op if entity is absent), so retries are safe.
            throw new InvoiceCompensationException(
                "Compensation failed for document " + documentId, e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<ProcessedInvoice> findById(String id) {
        try {
            return invoiceRepository.findById(InvoiceId.from(id));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid invoice ID format: {}", id);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<ProcessedInvoice> findByStatus(ProcessingStatus status) {
        return invoiceRepository.findByStatus(status);
    }
}
