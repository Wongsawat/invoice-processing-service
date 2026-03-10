package com.wpanther.invoice.processing.infrastructure.persistence;

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

    @Override
    @Transactional
    public ProcessedInvoice save(ProcessedInvoice invoice) {
        log.debug("Saving processed invoice: {}", invoice.getInvoiceNumber());

        UUID id = invoice.getId().value();
        if (jpaRepository.existsById(id)) {
            ProcessedInvoiceEntity existing = jpaRepository.getReferenceById(id);
            existing.setStatus(invoice.getStatus());
            existing.setErrorMessage(invoice.getErrorMessage());
            existing.setCompletedAt(invoice.getCompletedAt());
            jpaRepository.saveAndFlush(existing);
        } else {
            ProcessedInvoiceEntity entity = mapper.toEntity(invoice);
            jpaRepository.saveAndFlush(entity);
        }

        log.debug("Saved processed invoice: {} with ID: {}", invoice.getInvoiceNumber(), id);
        return invoice;
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

        return jpaRepository.findBySourceInvoiceId(sourceInvoiceId)
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
