package com.invoice.processing.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing a business party (seller or buyer)
 */
public record Party(
    String name,
    TaxIdentifier taxIdentifier,
    Address address,
    String email
) implements Serializable {

    public Party {
        Objects.requireNonNull(name, "Party name is required");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Party name cannot be blank");
        }
    }

    /**
     * Create party with minimal information
     */
    public static Party of(String name, TaxIdentifier taxIdentifier, Address address) {
        return new Party(name, taxIdentifier, address, null);
    }

    /**
     * Create party with all information
     */
    public static Party of(String name, TaxIdentifier taxIdentifier, Address address, String email) {
        return new Party(name, taxIdentifier, address, email);
    }

    /**
     * Check if party has email
     */
    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
