package com.wpanther.taxinvoice.processing.application.service;

import com.wpanther.taxinvoice.processing.domain.event.TaxInvoiceProcessedEvent;
import com.wpanther.taxinvoice.processing.domain.model.TaxInvoiceId;
import com.wpanther.taxinvoice.processing.domain.model.ProcessedTaxInvoice;
import com.wpanther.taxinvoice.processing.domain.model.ProcessingStatus;
import com.wpanther.taxinvoice.processing.domain.repository.ProcessedTaxInvoiceRepository;
import com.wpanther.taxinvoice.processing.domain.service.TaxInvoiceParserService;
import com.wpanther.taxinvoice.processing.infrastructure.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service for tax invoice processing.
 * Processes tax invoices as part of the saga orchestrator pattern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxInvoiceProcessingService {

    private final ProcessedTaxInvoiceRepository invoiceRepository;
    private final TaxInvoiceParserService parserService;
    private final EventPublisher eventPublisher;

    /**
     * Process tax invoice as part of a saga command.
     * Parses XML, validates, calculates totals, saves to DB, publishes notification event.
     * Does NOT publish xml.signing.requested (orchestrator handles next step).
     *
     * @throws TaxInvoiceParserService.TaxInvoiceParsingException on parse failure
     */
    @Transactional
    public ProcessedTaxInvoice processInvoiceForSaga(String documentId, String xmlContent,
                                                      String correlationId)
            throws TaxInvoiceParserService.TaxInvoiceParsingException {
        log.info("Processing tax invoice for saga, document: {}", documentId);

        // Idempotency check
        Optional<ProcessedTaxInvoice> existing = invoiceRepository.findBySourceInvoiceId(documentId);
        if (existing.isPresent()) {
            log.warn("Tax invoice already processed for document {}, returning existing", documentId);
            return existing.get();
        }

        // Parse XML to domain model
        ProcessedTaxInvoice invoice = parserService.parseInvoice(xmlContent, documentId);

        // State: PENDING → PROCESSING
        invoice.startProcessing();
        ProcessedTaxInvoice saved = invoiceRepository.save(invoice);
        log.info("Saved processed tax invoice: {}", saved.getInvoiceNumber());

        // State: PROCESSING → COMPLETED
        saved.markCompleted();
        invoiceRepository.save(saved);

        // Publish notification event (kept for notification-service)
        TaxInvoiceProcessedEvent processedEvent = new TaxInvoiceProcessedEvent(
            saved.getId().toString(),
            saved.getInvoiceNumber(),
            saved.getTotal().amount(),
            saved.getCurrency(),
            correlationId
        );
        eventPublisher.publishTaxInvoiceProcessed(processedEvent);

        log.info("Successfully processed tax invoice: {}", saved.getInvoiceNumber());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ProcessedTaxInvoice> findById(String id) {
        try {
            return invoiceRepository.findById(TaxInvoiceId.from(id));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tax invoice ID format: {}", id);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<ProcessedTaxInvoice> findByStatus(ProcessingStatus status) {
        return invoiceRepository.findByStatus(status);
    }
}
