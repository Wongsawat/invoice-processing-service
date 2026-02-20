package com.wpanther.taxinvoice.processing.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object representing an invoice line item
 */
public record LineItem(
    String description,
    int quantity,
    Money unitPrice,
    BigDecimal taxRate
) implements Serializable {

    public LineItem {
        Objects.requireNonNull(description, "Description is required");
        Objects.requireNonNull(unitPrice, "Unit price is required");
        Objects.requireNonNull(taxRate, "Tax rate is required");

        if (description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be blank");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100");
        }
    }

    /**
     * Calculate line total (before tax)
     */
    public Money getLineTotal() {
        return unitPrice.multiply(quantity);
    }

    /**
     * Calculate tax amount for this line
     */
    public Money getTaxAmount() {
        Money lineTotal = getLineTotal();
        BigDecimal taxMultiplier = taxRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return lineTotal.multiply(taxMultiplier);
    }

    /**
     * Calculate line total including tax
     */
    public Money getTotalWithTax() {
        return getLineTotal().add(getTaxAmount());
    }
}
