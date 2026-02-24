package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.ProcessingStatus;
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
import com.wpanther.invoice.processing.domain.service.InvoiceParserService;
import com.wpanther.invoice.processing.infrastructure.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service for invoice processing orchestration using Saga pattern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceProcessingService {

    private final ProcessedInvoiceRepository invoiceRepository;
    private final InvoiceParserService parserService;
    private final EventPublisher eventPublisher;

    /**
     * Find invoice by ID
     */
    @Transactional(readOnly = true)
    public Optional<ProcessedInvoice> findById(String id) {
        try {
            return invoiceRepository.findById(InvoiceId.from(id));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid invoice ID format: {}", id);
            return Optional.empty();
        }
    }

    /**
     * Find invoices by status
     */
    @Transactional(readOnly = true)
    public List<ProcessedInvoice> findByStatus(ProcessingStatus status) {
        return invoiceRepository.findByStatus(status);
    }

    /**
     * Process invoice as part of a saga command.
     * Parses XML, validates, calculates totals, saves to DB, publishes notification event.
     *
     * <p>This method is idempotent - if the same documentId is processed concurrently,
     * the unique constraint on source_invoice_id prevents duplicates. Any race condition
     * is handled by catching DataIntegrityViolationException and returning the existing invoice.
     *
     * @throws InvoiceParserService.InvoiceParsingException on parse failure
     */
    @Transactional
    public ProcessedInvoice processInvoiceForSaga(String documentId, String xmlContent,
                                                  String correlationId)
            throws InvoiceParserService.InvoiceParsingException {
        log.info("Processing invoice for saga, document: {}", documentId);

        // Fast path: check if invoice already exists
        Optional<ProcessedInvoice> existing = invoiceRepository.findBySourceInvoiceId(documentId);
        if (existing.isPresent()) {
            log.debug("Invoice already processed for document {}, returning existing", documentId);
            return existing.get();
        }

        try {
            // Parse XML to domain model
            ProcessedInvoice invoice = parserService.parseInvoice(xmlContent, documentId);

            // State: PENDING → PROCESSING
            invoice.startProcessing();
            ProcessedInvoice saved = invoiceRepository.save(invoice);
            log.info("Saved processed invoice: {}", saved.getInvoiceNumber());

            // State: PROCESSING → COMPLETED
            saved.markCompleted();
            invoiceRepository.save(saved);

            // Publish notification event for notification-service
            InvoiceProcessedEvent processedEvent = new InvoiceProcessedEvent(
                saved.getId().toString(),
                saved.getInvoiceNumber(),
                saved.getTotal().amount(),
                saved.getCurrency(),
                correlationId
            );
            eventPublisher.publishInvoiceProcessed(processedEvent);

            log.info("Successfully processed invoice for saga: {}", saved.getInvoiceNumber());
            return saved;

        } catch (DataIntegrityViolationException e) {
            // Race condition handling: another thread inserted the same invoice
            // between our check and save attempt. Fetch and return the existing one.
            log.info("Race condition detected for document {} - invoice was inserted by another transaction. Fetching existing invoice.",
                    documentId);

            Optional<ProcessedInvoice> existingInvoice = invoiceRepository.findBySourceInvoiceId(documentId);
            if (existingInvoice.isPresent()) {
                log.debug("Returning existing invoice after race condition for document: {}", documentId);
                return existingInvoice.get();
            }

            // This should never happen if the unique constraint is working correctly
            throw new IllegalStateException(
                    "Invoice not found after constraint violation for document: " + documentId, e);
        }
    }
}
