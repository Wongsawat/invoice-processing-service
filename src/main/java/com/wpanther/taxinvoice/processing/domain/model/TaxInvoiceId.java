package com.wpanther.taxinvoice.processing.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing Tax Invoice identifier
 */
public record TaxInvoiceId(UUID value) implements Serializable {

    public TaxInvoiceId {
        Objects.requireNonNull(value, "Tax Invoice ID cannot be null");
    }

    /**
     * Generate a new unique tax invoice ID
     */
    public static TaxInvoiceId generate() {
        return new TaxInvoiceId(UUID.randomUUID());
    }

    /**
     * Create tax invoice ID from string
     */
    public static TaxInvoiceId from(String id) {
        Objects.requireNonNull(id, "Tax Invoice ID string cannot be null");
        try {
            return new TaxInvoiceId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid tax invoice ID format: " + id, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
