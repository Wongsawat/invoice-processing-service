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
     * @throws InvoiceParserService.InvoiceParsingException on parse failure
     */
    @Transactional
    public ProcessedInvoice processInvoiceForSaga(String documentId, String xmlContent,
                                                  String correlationId)
            throws InvoiceParserService.InvoiceParsingException {
        log.info("Processing invoice for saga, document: {}", documentId);

        // Idempotency check
        Optional<ProcessedInvoice> existing = invoiceRepository.findBySourceInvoiceId(documentId);
        if (existing.isPresent()) {
            log.warn("Invoice already processed for document {}, returning existing", documentId);
            return existing.get();
        }

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
    }
}
