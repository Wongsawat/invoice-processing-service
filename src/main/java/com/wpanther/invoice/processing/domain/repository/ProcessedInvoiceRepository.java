package com.wpanther.invoice.processing.domain.repository;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.ProcessingStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ProcessedInvoice aggregate
 *
 * This is a domain-level repository that works with domain objects,
 * not JPA entities. The implementation handles the mapping.
 */
public interface ProcessedInvoiceRepository {

    /**
     * Save a processed invoice
     */
    ProcessedInvoice save(ProcessedInvoice invoice);

    /**
     * Find invoice by ID
     */
    Optional<ProcessedInvoice> findById(InvoiceId id);

    /**
     * Find invoice by invoice number
     */
    Optional<ProcessedInvoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find invoices by status
     */
    List<ProcessedInvoice> findByStatus(ProcessingStatus status);

    /**
     * Find invoice by source invoice ID
     */
    Optional<ProcessedInvoice> findBySourceInvoiceId(String sourceInvoiceId);

    /**
     * Check if invoice number already exists
     */
    boolean existsByInvoiceNumber(String invoiceNumber);

    /**
     * Delete invoice by ID
     */
    void deleteById(InvoiceId id);
}
