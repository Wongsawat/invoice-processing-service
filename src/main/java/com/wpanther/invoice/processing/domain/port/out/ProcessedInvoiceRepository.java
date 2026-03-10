package com.wpanther.invoice.processing.domain.port.out;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.ProcessingStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port — persistence contract for ProcessedInvoice aggregate.
 * Domain dictates the contract; infrastructure provides the implementation.
 */
public interface ProcessedInvoiceRepository {

    ProcessedInvoice save(ProcessedInvoice invoice);

    Optional<ProcessedInvoice> findById(InvoiceId id);

    Optional<ProcessedInvoice> findByInvoiceNumber(String invoiceNumber);

    List<ProcessedInvoice> findByStatus(ProcessingStatus status);

    Optional<ProcessedInvoice> findBySourceInvoiceId(String sourceInvoiceId);

    boolean existsByInvoiceNumber(String invoiceNumber);

    void deleteById(InvoiceId id);
}
