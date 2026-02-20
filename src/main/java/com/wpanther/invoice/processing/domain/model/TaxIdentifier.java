package com.wpanther.invoice.processing.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing a tax identifier (VAT, EIN, etc.)
 */
public record TaxIdentifier(String value, String scheme) implements Serializable {

    public TaxIdentifier {
        Objects.requireNonNull(value, "Tax identifier value cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("Tax identifier cannot be blank");
        }
    }

    /**
     * Create tax identifier with default scheme
     */
    public static TaxIdentifier of(String value) {
        return new TaxIdentifier(value, "VAT");
    }

    /**
     * Create tax identifier with specific scheme
     */
    public static TaxIdentifier of(String value, String scheme) {
        return new TaxIdentifier(value, scheme);
    }

    @Override
    public String toString() {
        if (scheme != null && !scheme.isBlank()) {
            return scheme + ":" + value;
        }
        return value;
    }
}
