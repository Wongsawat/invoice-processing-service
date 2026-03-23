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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    // Metrics — initialized once in constructor
    private final Counter processSuccessCounter;
    private final Counter processFailureCounter;
    private final Counter processIdempotentCounter;
    private final Counter processRaceConditionResolvedCounter;
    private final Counter compensateSuccessCounter;
    private final Counter compensateIdempotentCounter;
    private final Counter compensateFailureCounter;
    private final Timer processingTimer;

    public InvoiceProcessingService(
            ProcessedInvoiceRepository invoiceRepository,
            InvoiceParserPort parserPort,
            SagaReplyPort sagaReplyPort,
            InvoiceEventPublishingPort eventPublishingPort,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.invoiceRepository = invoiceRepository;
        this.parserPort = parserPort;
        this.sagaReplyPort = sagaReplyPort;
        this.eventPublishingPort = eventPublishingPort;

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTemplate = template;

        // Initialize metrics once
        this.processSuccessCounter = Counter.builder("invoice.processing.success")
            .description("Number of successfully processed invoices")
            .register(meterRegistry);
        this.processFailureCounter = Counter.builder("invoice.processing.failure")
            .description("Number of failed invoice processing attempts")
            .register(meterRegistry);
        this.processIdempotentCounter = Counter.builder("invoice.processing.idempotent")
            .description("Number of duplicate processing requests handled idempotently")
            .register(meterRegistry);
        this.processRaceConditionResolvedCounter = Counter.builder("invoice.processing.race_condition_resolved")
            .description("Number of DuplicateKeyExceptions on source_invoice_id resolved as concurrent inserts — re-check confirmed the document was committed by another thread")
            .register(meterRegistry);
        this.compensateSuccessCounter = Counter.builder("invoice.compensation.success")
            .description("Number of successful compensations")
            .register(meterRegistry);
        this.compensateIdempotentCounter = Counter.builder("invoice.compensation.idempotent")
            .description("Number of duplicate compensation commands received for an already-deleted invoice")
            .register(meterRegistry);
        this.compensateFailureCounter = Counter.builder("invoice.compensation.failure")
            .description("Number of failed compensation attempts")
            .register(meterRegistry);
        this.processingTimer = Timer.builder("invoice.processing.duration")
            .description("Time taken to process invoices")
            .register(meterRegistry);
    }

    @Override
    @Transactional
    public void process(String documentId, String xmlContent,
                        String sagaId, SagaStep sagaStep, String correlationId)
            throws InvoiceProcessingException {
        log.info("Processing invoice for saga={} document={}", sagaId, documentId);
        Timer.Sample sample = Timer.start();

        // Declared outside the try block so the catch block can reference them.
        // processingStateSaved: first save (PROCESSING) committed — entity exists in DB.
        // completedStateSaved: second save (COMPLETED) committed — entity is in final state.
        ProcessedInvoice invoice = null;
        boolean processingStateSaved = false;
        boolean completedStateSaved = false;

        try {
            // Idempotency check — also resumes partial-failure where a previous attempt
            // saved the entity in PROCESSING state but died before reaching COMPLETED.
            Optional<ProcessedInvoice> existing = invoiceRepository.findBySourceInvoiceId(documentId);
            if (existing.isPresent()) {
                ProcessedInvoice existingInvoice = existing.get();

                if (existingInvoice.getStatus() == ProcessingStatus.COMPLETED) {
                    // True idempotent case: a prior attempt fully committed this document.
                    log.warn("Invoice already completed for document={}, returning idempotent success", documentId);
                    processIdempotentCounter.increment();
                    sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                    return;
                }

                if (existingInvoice.getStatus() == ProcessingStatus.PROCESSING) {
                    // Partial failure: entity was inserted (PROCESSING) but the attempt died
                    // before markCompleted() + second save could commit. Resume from here
                    // without re-parsing or re-inserting — avoids a duplicate-key violation
                    // and ensures the orchestrator always receives a SUCCESS reply.
                    log.warn("Invoice for document={} found in PROCESSING state — previous attempt "
                            + "failed mid-flight; resuming completion", documentId);
                    existingInvoice.markCompleted();
                    invoiceRepository.save(existingInvoice);
                    eventPublishingPort.publish(InvoiceProcessedDomainEvent.of(
                        existingInvoice.getId(),
                        existingInvoice.getInvoiceNumber(),
                        existingInvoice.getTotal(),
                        sagaId,
                        correlationId
                    ));
                    sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                    processSuccessCounter.increment();
                    log.info("Resumed and completed invoice={} for saga={}", existingInvoice.getInvoiceNumber(), sagaId);
                    return;
                }

                // PENDING is never persisted; FAILED surfaces here only if a prior run
                // was killed after persistFailedState() committed but before the outer
                // transaction rolled back (extremely rare). Treat as an error rather than
                // silently mis-routing.
                throw new IllegalStateException(
                    "Invoice for document " + documentId + " has unexpected persisted status: "
                        + existingInvoice.getStatus());
            }

            invoice = parserPort.parse(xmlContent, documentId);

            invoice.startProcessing();
            invoiceRepository.save(invoice);
            processingStateSaved = true; // entity is now in DB as PROCESSING

            invoice.markCompleted();
            invoiceRepository.save(invoice);
            completedStateSaved = true; // entity is now in DB as COMPLETED

            eventPublishingPort.publish(InvoiceProcessedDomainEvent.of(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getTotal(),
                sagaId,
                correlationId
            ));

            sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
            processSuccessCounter.increment();
            log.info("Successfully processed invoice={} for saga={}", invoice.getInvoiceNumber(), sagaId);

        } catch (DuplicateKeyException e) {
            // Only the source_invoice_id constraint violation indicates a potential race condition
            // (two threads inserting the same document concurrently). Any other unique constraint
            // violation (e.g. duplicate invoice_number from a different document) is a data error
            // and must fail immediately without a REQUIRES_NEW re-check.
            if (!isSourceInvoiceIdViolation(e)) {
                processFailureCounter.increment();
                log.error("Duplicate key violation on non-idempotent constraint for document {}, saga {}: {}",
                        documentId, sagaId, e.toString());
                sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                        "Constraint violation processing document " + documentId);
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
                    processRaceConditionResolvedCounter.increment();
                    sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                } else {
                    // source_invoice_id index fired but no record found — unexpected state.
                    log.error("DuplicateKeyException on source_invoice_id for document {} but no record found — replying FAILURE",
                            documentId);
                    processFailureCounter.increment();
                    sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                            "Duplicate key violation processing document " + documentId);
                }
                return null;
            });
            // Always throw so Spring does not try to commit the ROLLBACK_ONLY outer
            // transaction (which would raise UnexpectedRollbackException past SagaCommandHandler).
            throw new InvoiceProcessingException("Concurrent insert for document: " + documentId, e);

        } catch (DataIntegrityViolationException e) {
            // Other constraint violations (value-too-long, check-constraint, etc.).
            // These are not race-condition duplicates and must not be treated as idempotent.
            processFailureCounter.increment();
            log.error("Constraint violation (non-duplicate-key) for document {}, saga {}: {}",
                    documentId, sagaId, e.toString());
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                    "Constraint violation processing document " + documentId);
            throw new InvoiceProcessingException(
                    "Constraint violation for document " + documentId, e);

        } catch (Exception e) {
            processFailureCounter.increment();
            log.error("Failed to process invoice for saga={} document={}: {}",
                sagaId, documentId, e.getMessage(), e);

            // Persist FAILED status so operators can query failed documents.
            // Only attempted when the entity was saved in PROCESSING state but not yet
            // updated to COMPLETED — i.e., the DB row exists and still shows PROCESSING.
            // Runs in a REQUIRES_NEW transaction because the outer @Transactional is
            // ROLLBACK_ONLY at this point. Failure to update FAILED state is logged
            // as a warning but does not mask the original exception.
            if (processingStateSaved && !completedStateSaved && invoice != null) {
                persistFailedState(invoice, e, documentId);
            }

            // publishFailure uses REQUIRES_NEW — commits independently even if the outer
            // transaction is ROLLBACK_ONLY (e.g. after a DB error from save above).
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                "Processing error for document " + documentId);
            // Throw so the caller (SagaCommandHandler) knows the FAILURE reply was committed
            // and can return normally to Camel, committing the Kafka offset without retrying.
            throw new InvoiceProcessingException(
                "Failed to process invoice " + documentId + ": " + e, e);
        } finally {
            sample.stop(processingTimer);
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
                    () -> {
                        compensateIdempotentCounter.increment();
                        log.info("No invoice found for document={} — already compensated or never processed",
                            documentId);
                    }
                );

            sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
            compensateSuccessCounter.increment();
        } catch (Exception e) {
            compensateFailureCounter.increment();
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
     * Persists {@link ProcessingStatus#FAILED} on an invoice that was previously saved as
     * {@link ProcessingStatus#PROCESSING} but could not reach {@link ProcessingStatus#COMPLETED}.
     *
     * <p>Runs in a {@code REQUIRES_NEW} transaction because the outer {@code @Transactional}
     * is already marked ROLLBACK_ONLY when this method is called from the catch block.
     * A failure here is logged as a warning and suppressed — the FAILURE saga reply
     * (published immediately after) is the higher-priority outcome for the orchestrator.
     *
     * @param invoice      the domain object to mark as failed (mutated in place)
     * @param cause        the exception that caused the failure (message stored on the entity)
     * @param documentId   used only for logging
     */
    private void persistFailedState(ProcessedInvoice invoice, Exception cause, String documentId) {
        try {
            String errorMessage = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            invoice.markFailed(errorMessage);
            requiresNewTemplate.execute(txStatus -> {
                invoiceRepository.save(invoice);
                return null;
            });
            log.info("Persisted FAILED status for invoice document={}", documentId);
        } catch (Exception saveEx) {
            log.warn("Failed to persist FAILED status for document={} — entity may remain as PROCESSING: {}",
                documentId, saveEx.getMessage());
        }
    }

    /**
     * The name of the unique index on {@code processed_invoices.source_invoice_id},
     * as declared in the Flyway V1 migration. Published as a package-visible constant
     * so that {@code SchemaInvariantValidator} can verify the index exists at startup
     * without duplicating the literal string.
     */
    public static final String SOURCE_INVOICE_ID_INDEX = "idx_source_invoice_id";

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
     *       The index name is declared in {@link #SOURCE_INVOICE_ID_INDEX} and verified
     *       at startup by {@code SchemaInvariantValidator}.</li>
     * </ol>
     */
    private static boolean isSourceInvoiceIdViolation(DuplicateKeyException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = cause.getMessage();
        if (msg == null || !msg.contains(SOURCE_INVOICE_ID_INDEX)) {
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
