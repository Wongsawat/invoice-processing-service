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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Application service implementing both use case inbound ports.
 * Coordinates domain logic and all four outbound ports.
 * Zero imports from infrastructure — dependency rule enforced by package structure.
 */
@Service
@Slf4j
public class InvoiceProcessingService
        implements ProcessInvoiceUseCase, CompensateInvoiceUseCase {

    private final ProcessedInvoiceRepository invoiceRepository;
    private final InvoiceParserPort parserPort;
    private final SagaReplyPort sagaReplyPort;
    private final InvoiceEventPublishingPort eventPublishingPort;

    // Fresh-transaction executor for replying after a ROLLBACK_ONLY outer transaction
    private final TransactionTemplate requiresNewTemplate;

    public InvoiceProcessingService(
            ProcessedInvoiceRepository invoiceRepository,
            InvoiceParserPort parserPort,
            SagaReplyPort sagaReplyPort,
            InvoiceEventPublishingPort eventPublishingPort,
            PlatformTransactionManager transactionManager) {
        this.invoiceRepository = invoiceRepository;
        this.parserPort = parserPort;
        this.sagaReplyPort = sagaReplyPort;
        this.eventPublishingPort = eventPublishingPort;

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTemplate = template;
    }

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

        } catch (DuplicateKeyException e) {
            // Only the source_invoice_id constraint violation indicates a potential race condition
            // (two threads inserting the same document concurrently). Any other unique constraint
            // violation (e.g. duplicate invoice_number from a different document) is a data error
            // and must fail immediately without a REQUIRES_NEW re-check.
            if (!isSourceInvoiceIdViolation(e)) {
                log.error("Duplicate key violation on non-idempotent constraint for document {}, saga {}: {}",
                        documentId, sagaId, e.toString());
                sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                        "Constraint violation for document " + documentId + ": " + e.toString());
                throw new InvoiceProcessingException(
                        "Constraint violation for document " + documentId, e);
            }

            // Race-condition duplicate insert on source_invoice_id unique index.
            // The outer transaction is ROLLBACK_ONLY; re-check in a fresh REQUIRES_NEW
            // transaction so we can reply SUCCESS if a concurrent thread already committed
            // the document — preventing the orchestrator from compensating committed work.
            log.warn("DuplicateKeyException on source_invoice_id for document {}, saga {} — re-checking for concurrent insert",
                    documentId, sagaId);
            requiresNewTemplate.execute(txStatus -> {
                Optional<ProcessedInvoice> existing = invoiceRepository.findBySourceInvoiceId(documentId);
                if (existing.isPresent()) {
                    // Concurrent thread committed the same document first; treat as idempotent success.
                    log.warn("Race condition resolved: document {} already committed by concurrent thread — replying SUCCESS",
                            documentId);
                    sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                } else {
                    // source_invoice_id index fired but no record found — unexpected state.
                    log.error("DuplicateKeyException on source_invoice_id for document {} but no record found — replying FAILURE",
                            documentId);
                    sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                            "Duplicate key violation for document " + documentId + ": " + e.toString());
                }
                return null;
            });
            // Always throw so Spring does not try to commit the ROLLBACK_ONLY outer
            // transaction (which would raise UnexpectedRollbackException past SagaCommandHandler).
            throw new InvoiceProcessingException("Concurrent insert for document: " + documentId, e);

        } catch (DataIntegrityViolationException e) {
            // Other constraint violations (value-too-long, check-constraint, etc.).
            // These are not race-condition duplicates and must not be treated as idempotent.
            log.error("Constraint violation (non-duplicate-key) for document {}, saga {}: {}",
                    documentId, sagaId, e.toString());
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                    "Constraint violation for document " + documentId + ": " + e.toString());
            throw new InvoiceProcessingException(
                    "Constraint violation for document " + documentId, e);

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

    /**
     * Returns {@code true} only when the exception is specifically a unique_violation
     * on the {@code idx_source_invoice_id} index — the sole case that indicates a
     * concurrent insert of the same document rather than a genuine data error.
     *
     * <p>Detection strategy (two independent guards, both must match):
     * <ol>
     *   <li><b>SQLState "23505"</b> — the ANSI / PostgreSQL / H2 code for
     *       {@code unique_violation}. Stable across DB versions and drivers.</li>
     *   <li><b>Index name in the message</b> — narrows the match to
     *       <em>this specific</em> index so that a duplicate {@code invoice_number}
     *       (a different unique index) is not treated as an idempotent race condition.
     *       The index name is set by Flyway V1 and must stay in sync if ever renamed.</li>
     * </ol>
     */
    private static boolean isSourceInvoiceIdViolation(DuplicateKeyException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = cause.getMessage();
        if (msg == null || !msg.contains("idx_source_invoice_id")) {
            return false;
        }
        // Walk the cause chain for a SQLException carrying SQLState "23505".
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
