package com.invoice.processing.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing Invoice identifier
 */
public record InvoiceId(UUID value) implements Serializable {

    public InvoiceId {
        Objects.requireNonNull(value, "Invoice ID cannot be null");
    }

    /**
     * Generate a new unique invoice ID
     */
    public static InvoiceId generate() {
        return new InvoiceId(UUID.randomUUID());
    }

    /**
     * Create invoice ID from string
     */
    public static InvoiceId from(String id) {
        Objects.requireNonNull(id, "Invoice ID string cannot be null");
        try {
            return new InvoiceId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid invoice ID format: " + id, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
