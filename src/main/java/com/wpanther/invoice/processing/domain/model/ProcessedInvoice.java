package com.wpanther.invoice.processing.domain.model;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate Root representing a processed invoice
 *
 * This is the core domain entity that encapsulates invoice business logic,
 * including calculations, validations, and state management.
 */
public class ProcessedInvoice {

    // Identity
    private final InvoiceId id;
    private final String sourceInvoiceId;

    // Invoice Header
    private final String invoiceNumber;
    private final LocalDate issueDate;
    private final LocalDate dueDate;

    // Parties
    private final Party seller;
    private final Party buyer;

    // Line Items
    private final List<LineItem> items;

    // Currency
    private final String currency;

    // Original XML Content
    private final String originalXml;

    // Processing Metadata
    private ProcessingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    // Domain events raised during aggregate lifecycle
    private final List<Object> domainEvents = new ArrayList<>();

    // Cached totals (calculated on demand)
    private transient Money cachedSubtotal;
    private transient Money cachedTotalTax;
    private transient Money cachedTotal;

    private ProcessedInvoice(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Invoice ID is required");
        this.sourceInvoiceId = Objects.requireNonNull(builder.sourceInvoiceId, "Source invoice ID is required");
        this.invoiceNumber = Objects.requireNonNull(builder.invoiceNumber, "Invoice number is required");
        this.issueDate = Objects.requireNonNull(builder.issueDate, "Issue date is required");
        this.dueDate = Objects.requireNonNull(builder.dueDate, "Due date is required");
        this.seller = Objects.requireNonNull(builder.seller, "Seller is required");
        this.buyer = Objects.requireNonNull(builder.buyer, "Buyer is required");
        this.items = new ArrayList<>(Objects.requireNonNull(builder.items, "Items are required"));
        this.currency = Objects.requireNonNull(builder.currency, "Currency is required");
        this.originalXml = Objects.requireNonNull(builder.originalXml, "Original XML is required");
        this.status = builder.status != null ? builder.status : ProcessingStatus.PENDING;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.completedAt = builder.completedAt;
        this.errorMessage = builder.errorMessage;

        // Validate business rules
        validateInvariant();
    }

    /**
     * Validate business invariants
     */
    private void validateInvariant() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Invoice must have at least one line item");
        }

        if (dueDate.isBefore(issueDate)) {
            throw new IllegalStateException("Due date cannot be before issue date");
        }

        if (currency.length() != 3) {
            throw new IllegalStateException("Currency must be a 3-letter ISO code");
        }

        // Validate all items have same currency as invoice
        items.forEach(item -> {
            if (!item.unitPrice().currency().equals(currency)) {
                throw new IllegalStateException(
                    String.format("Line item currency %s does not match invoice currency %s",
                        item.unitPrice().currency(), currency)
                );
            }
        });
    }

    /**
     * Calculate invoice subtotal (sum of all line totals before tax)
     */
    public Money getSubtotal() {
        if (cachedSubtotal == null) {
            cachedSubtotal = items.stream()
                .map(LineItem::getLineTotal)
                .reduce(Money.zero(currency), Money::add);
        }
        return cachedSubtotal;
    }

    /**
     * Calculate total tax amount
     */
    public Money getTotalTax() {
        if (cachedTotalTax == null) {
            cachedTotalTax = items.stream()
                .map(LineItem::getTaxAmount)
                .reduce(Money.zero(currency), Money::add);
        }
        return cachedTotalTax;
    }

    /**
     * Calculate grand total (subtotal + tax)
     */
    public Money getTotal() {
        if (cachedTotal == null) {
            cachedTotal = getSubtotal().add(getTotalTax());
        }
        return cachedTotal;
    }

    /**
     * Mark invoice as processing
     */
    public void startProcessing() {
        if (status != ProcessingStatus.PENDING) {
            throw new IllegalStateException("Can only start processing from PENDING status");
        }
        this.status = ProcessingStatus.PROCESSING;
    }

    /**
     * Mark invoice processing as completed.
     * Raises InvoiceProcessedDomainEvent.
     *
     * @param correlationId The saga correlation ID for event tracing
     */
    public void markCompleted(String correlationId) {
        if (status != ProcessingStatus.PROCESSING) {
            throw new IllegalStateException("Can only complete from PROCESSING status");
        }
        this.status = ProcessingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        domainEvents.add(new InvoiceProcessedDomainEvent(
            this.id,
            this.invoiceNumber,
            this.getTotal(),
            correlationId,
            Instant.now()
        ));
    }

    /**
     * Mark invoice processing as failed
     */
    public void markFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Returns an unmodifiable view of domain events raised since last clear.
     */
    public List<Object> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all domain events. Call after the application layer has processed them.
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // Getters
    public InvoiceId getId() {
        return id;
    }

    public String getSourceInvoiceId() {
        return sourceInvoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Party getSeller() {
        return seller;
    }

    public Party getBuyer() {
        return buyer;
    }

    public List<LineItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public String getCurrency() {
        return currency;
    }

    public String getOriginalXml() {
        return originalXml;
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Builder for ProcessedInvoice
     */
    public static class Builder {
        private InvoiceId id;
        private String sourceInvoiceId;
        private String invoiceNumber;
        private LocalDate issueDate;
        private LocalDate dueDate;
        private Party seller;
        private Party buyer;
        private List<LineItem> items = new ArrayList<>();
        private String currency;
        private String originalXml;
        private ProcessingStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private String errorMessage;

        public Builder id(InvoiceId id) {
            this.id = id;
            return this;
        }

        public Builder sourceInvoiceId(String sourceInvoiceId) {
            this.sourceInvoiceId = sourceInvoiceId;
            return this;
        }

        public Builder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public Builder issueDate(LocalDate issueDate) {
            this.issueDate = issueDate;
            return this;
        }

        public Builder dueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder seller(Party seller) {
            this.seller = seller;
            return this;
        }

        public Builder buyer(Party buyer) {
            this.buyer = buyer;
            return this;
        }

        public Builder items(List<LineItem> items) {
            this.items = items;
            return this;
        }

        public Builder addItem(LineItem item) {
            this.items.add(item);
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder originalXml(String originalXml) {
            this.originalXml = originalXml;
            return this;
        }

        public Builder status(ProcessingStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ProcessedInvoice build() {
            return new ProcessedInvoice(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
