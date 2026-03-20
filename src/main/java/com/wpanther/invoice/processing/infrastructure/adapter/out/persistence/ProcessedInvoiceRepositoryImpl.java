package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.ProcessingStatus;
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of ProcessedInvoiceRepository using Spring Data JPA
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ProcessedInvoiceRepositoryImpl implements ProcessedInvoiceRepository {

    private final JpaProcessedInvoiceRepository jpaRepository;
    private final ProcessedInvoiceMapper mapper;

    /**
     * Persists a {@link ProcessedInvoice}, using its current status as a zero-cost
     * INSERT vs UPDATE discriminator.
     *
     * <p><b>CONTRACT — callers must honour the following invariant:</b>
     * <ul>
     *   <li>The <em>first</em> call for any given invoice ID <strong>must</strong> pass a
     *       {@link ProcessingStatus#PROCESSING} invoice.  The application service guarantees
     *       this by calling {@code startProcessing()} (PENDING → PROCESSING) before the
     *       initial {@code save()}.  PENDING is therefore never committed to the database.</li>
     *   <li>All <em>subsequent</em> calls for the same ID must pass a non-PROCESSING status
     *       (e.g. COMPLETED, FAILED) — these take the UPDATE path.</li>
     * </ul>
     *
     * <p>Violating the contract by passing a non-PROCESSING invoice that has never been
     * INSERTed is detected by an {@link IllegalStateException} thrown unconditionally
     * (not {@code assert}) so the failure is loud in all environments, including production.
     */
    @Override
    @Transactional
    public ProcessedInvoice save(ProcessedInvoice invoice) {
        log.debug("Saving processed invoice: {}", invoice.getInvoiceNumber());

        UUID id = invoice.getId().value();

        ProcessedInvoice result;
        // PROCESSING is the first status ever persisted: the service always calls
        // startProcessing() before the initial save(), so PENDING is never committed
        // to the database. Use this as a zero-cost insert/update discriminator,
        // eliminating the existsById SELECT that would otherwise be needed on every call.
        if (invoice.getStatus() == ProcessingStatus.PROCESSING) {
            // New entity — full mapping. saveAndFlush forces the INSERT immediately
            // so that @CreationTimestamp/@Version are populated on the returned entity
            // and unique-constraint violations are surfaced at call-site (not deferred).
            ProcessedInvoiceEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(invoice));
            result = mapper.toDomain(saved);
        } else {
            // Guard: a non-PROCESSING invoice passed to save() must already exist in the
            // database — otherwise the UPDATE below silently affects zero rows (data loss).
            // Throws unconditionally (not assert) so contract violations are caught in
            // production regardless of whether the JVM was started with -ea.
            if (!jpaRepository.existsById(id)) {
                throw new IllegalStateException(
                    "save() called with non-PROCESSING status on unpersisted invoice: " + id
                    + " (status=" + invoice.getStatus() + "). Callers must call startProcessing()"
                    + " and save() once with PROCESSING status before updating to another status.");
            }

            // Existing entity — update only mutable fields via direct UPDATE,
            // avoiding a full SELECT + dirty-check cycle on every state transition.
            jpaRepository.updateStatusFields(
                id, invoice.getStatus(), invoice.getErrorMessage(), invoice.getCompletedAt());
            result = invoice;
        }

        log.info("Saved processed invoice: {} with ID: {}", invoice.getInvoiceNumber(), id);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedInvoice> findById(InvoiceId id) {
        log.debug("Finding invoice by ID: {}", id);

        return jpaRepository.findByIdWithDetails(id.value())
            .map(entity -> {
                log.debug("Found invoice: {}", entity.getInvoiceNumber());
                return mapper.toDomain(entity);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedInvoice> findByInvoiceNumber(String invoiceNumber) {
        log.debug("Finding invoice by number: {}", invoiceNumber);

        return jpaRepository.findByInvoiceNumber(invoiceNumber)
            .map(entity -> {
                log.debug("Found invoice with ID: {}", entity.getId());
                return mapper.toDomain(entity);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessedInvoice> findByStatus(ProcessingStatus status) {
        log.debug("Finding invoices by status: {}", status);

        List<ProcessedInvoiceEntity> entities = jpaRepository.findByStatusWithDetails(status);
        log.debug("Found {} invoices with status: {}", entities.size(), status);

        return entities.stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedInvoice> findBySourceInvoiceId(String sourceInvoiceId) {
        log.debug("Finding invoice by source ID: {}", sourceInvoiceId);

        return jpaRepository.findBySourceInvoiceIdWithDetails(sourceInvoiceId)
            .map(entity -> {
                log.debug("Found invoice: {}", entity.getInvoiceNumber());
                return mapper.toDomain(entity);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByInvoiceNumber(String invoiceNumber) {
        boolean exists = jpaRepository.existsByInvoiceNumber(invoiceNumber);
        log.debug("Invoice number {} exists: {}", invoiceNumber, exists);
        return exists;
    }

    @Override
    @Transactional
    public void deleteById(InvoiceId id) {
        log.info("Deleting invoice with ID: {}", id);
        jpaRepository.deleteById(id.value());
    }
}
