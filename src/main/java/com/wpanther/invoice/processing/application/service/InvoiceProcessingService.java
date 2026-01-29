package com.wpanther.invoice.processing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedEvent;
import com.wpanther.invoice.processing.domain.event.InvoiceReceivedEvent;
import com.wpanther.invoice.processing.domain.event.XmlSigningRequestedEvent;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for invoice processing orchestration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceProcessingService {

    private final ProcessedInvoiceRepository invoiceRepository;
    private final InvoiceParserService parserService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Process invoice received from intake service
     */
    @Transactional
    public void processInvoiceReceived(InvoiceReceivedEvent event) {
        log.info("Processing invoice received event for invoice: {}", event.getInvoiceNumber());

        try {
            // Check if already processed
            Optional<ProcessedInvoice> existing = invoiceRepository.findBySourceInvoiceId(event.getDocumentId());
            if (existing.isPresent()) {
                log.warn("Invoice {} already processed, skipping", event.getInvoiceNumber());
                return;
            }

            // Parse XML to domain model
            ProcessedInvoice invoice = parserService.parseInvoice(
                event.getXmlContent(),
                event.getDocumentId()
            );

            // Start processing
            invoice.startProcessing();

            // Save invoice
            ProcessedInvoice saved = invoiceRepository.save(invoice);
            log.info("Saved processed invoice: {}", saved.getInvoiceNumber());

            // Complete processing
            saved.markCompleted();
            invoiceRepository.save(saved);

            // Publish invoice processed event
            InvoiceProcessedEvent processedEvent = new InvoiceProcessedEvent(
                saved.getId().toString(),
                saved.getInvoiceNumber(),
                saved.getTotal().amount(),
                saved.getCurrency(),
                event.getCorrelationId()
            );
            eventPublisher.publishInvoiceProcessed(processedEvent);

            // Request XML signing
            saved.requestPdfGeneration(); // Note: this state transition is still relevant
            invoiceRepository.save(saved);

            // Prepare invoice data JSON
            String invoiceDataJson = createInvoiceDataJson(saved);

            // Publish XML signing request
            XmlSigningRequestedEvent xmlSigningEvent = new XmlSigningRequestedEvent(
                saved.getId().toString(),
                saved.getInvoiceNumber(),
                saved.getOriginalXml(),
                invoiceDataJson,
                event.getCorrelationId()
            );
            eventPublisher.publishXmlSigningRequested(xmlSigningEvent);

            log.info("Successfully processed invoice: {}", saved.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Failed to process invoice: {}", event.getInvoiceNumber(), e);
            // In a real system, publish failure event and implement retry logic
        }
    }

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
     * Create JSON representation of invoice data for PDF generation
     */
    private String createInvoiceDataJson(ProcessedInvoice invoice) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("invoiceNumber", invoice.getInvoiceNumber());
            data.put("issueDate", invoice.getIssueDate().toString());
            data.put("dueDate", invoice.getDueDate().toString());
            data.put("currency", invoice.getCurrency());
            data.put("subtotal", invoice.getSubtotal().amount().toString());
            data.put("totalTax", invoice.getTotalTax().amount().toString());
            data.put("total", invoice.getTotal().amount().toString());

            // Add seller
            Map<String, String> seller = new HashMap<>();
            seller.put("name", invoice.getSeller().name());
            seller.put("address", invoice.getSeller().address().toSingleLine());
            data.put("seller", seller);

            // Add buyer
            Map<String, String> buyer = new HashMap<>();
            buyer.put("name", invoice.getBuyer().name());
            buyer.put("address", invoice.getBuyer().address().toSingleLine());
            data.put("buyer", buyer);

            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to create invoice data JSON", e);
            return "{}";
        }
    }
}
