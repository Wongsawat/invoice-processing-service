package com.wpanther.taxinvoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LineItem value object
 */
class LineItemTest {

    @Test
    void testCreateValidLineItem() {
        // Given
        String description = "Professional Services";
        int quantity = 10;
        Money unitPrice = Money.of(5000.00, "THB");
        BigDecimal taxRate = new BigDecimal("7.00");

        // When
        LineItem lineItem = new LineItem(description, quantity, unitPrice, taxRate);

        // Then
        assertNotNull(lineItem);
        assertEquals(description, lineItem.description());
        assertEquals(quantity, lineItem.quantity());
        assertEquals(unitPrice, lineItem.unitPrice());
        assertEquals(taxRate, lineItem.taxRate());
    }

    @Test
    void testCalculateLineTotal() {
        // Given
        LineItem lineItem = new LineItem(
            "Services",
            10,
            Money.of(5000.00, "THB"),
            new BigDecimal("7.00")
        );

        // When
        Money lineTotal = lineItem.getLineTotal();

        // Then
        assertEquals(Money.of(50000.00, "THB"), lineTotal);
    }

    @Test
    void testCalculateTaxAmount() {
        // Given
        LineItem lineItem = new LineItem(
            "Services",
            10,
            Money.of(5000.00, "THB"),
            new BigDecimal("7.00")
        );

        // When
        Money taxAmount = lineItem.getTaxAmount();

        // Then
        // Tax: 50000 * 0.07 = 3500
        assertEquals(Money.of(3500.00, "THB"), taxAmount);
    }

    @Test
    void testCalculateTotalWithTax() {
        // Given
        LineItem lineItem = new LineItem(
            "Services",
            10,
            Money.of(5000.00, "THB"),
            new BigDecimal("7.00")
        );

        // When
        Money totalWithTax = lineItem.getTotalWithTax();

        // Then
        // Total: 50000 + 3500 = 53500
        assertEquals(Money.of(53500.00, "THB"), totalWithTax);
    }

    @Test
    void testZeroTaxRate() {
        // Given
        LineItem lineItem = new LineItem(
            "Services",
            5,
            Money.of(1000.00, "THB"),
            BigDecimal.ZERO
        );

        // When
        Money taxAmount = lineItem.getTaxAmount();
        Money totalWithTax = lineItem.getTotalWithTax();

        // Then
        assertEquals(Money.of(0.00, "THB"), taxAmount);
        assertEquals(Money.of(5000.00, "THB"), totalWithTax);
    }

    @Test
    void testMaxTaxRate() {
        // Given
        LineItem lineItem = new LineItem(
            "Services",
            1,
            Money.of(100.00, "THB"),
            new BigDecimal("100.00")
        );

        // When
        Money taxAmount = lineItem.getTaxAmount();

        // Then
        assertEquals(Money.of(100.00, "THB"), taxAmount);
    }

    @Test
    void testNullDescription() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new LineItem(null, 10, Money.of(100.00, "THB"), new BigDecimal("7.00"))
        );
    }

    @Test
    void testBlankDescription() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new LineItem("", 10, Money.of(100.00, "THB"), new BigDecimal("7.00"))
        );

        assertThrows(IllegalArgumentException.class, () ->
            new LineItem("   ", 10, Money.of(100.00, "THB"), new BigDecimal("7.00"))
        );
    }

    @Test
    void testNullUnitPrice() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new LineItem("Services", 10, null, new BigDecimal("7.00"))
        );
    }

    @Test
    void testNullTaxRate() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new LineItem("Services", 10, Money.of(100.00, "THB"), null)
        );
    }

    @Test
    void testZeroQuantity() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new LineItem("Services", 0, Money.of(100.00, "THB"), new BigDecimal("7.00"))
        );
    }

    @Test
    void testNegativeQuantity() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new LineItem("Services", -5, Money.of(100.00, "THB"), new BigDecimal("7.00"))
        );
    }

    @Test
    void testNegativeTaxRate() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new LineItem("Services", 10, Money.of(100.00, "THB"), new BigDecimal("-1.00"))
        );
    }

    @Test
    void testTaxRateAbove100() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new LineItem("Services", 10, Money.of(100.00, "THB"), new BigDecimal("101.00"))
        );
    }

    @Test
    void testEquality() {
        // Given
        LineItem item1 = new LineItem("Services", 10, Money.of(100.00, "THB"), new BigDecimal("7.00"));
        LineItem item2 = new LineItem("Services", 10, Money.of(100.00, "THB"), new BigDecimal("7.00"));
        LineItem item3 = new LineItem("Products", 10, Money.of(100.00, "THB"), new BigDecimal("7.00"));

        // When/Then
        assertEquals(item1, item2);
        assertNotEquals(item1, item3);
    }

    @Test
    void testHashCode() {
        // Given
        LineItem item1 = new LineItem("Services", 10, Money.of(100.00, "THB"), new BigDecimal("7.00"));
        LineItem item2 = new LineItem("Services", 10, Money.of(100.00, "THB"), new BigDecimal("7.00"));

        // When/Then
        assertEquals(item1.hashCode(), item2.hashCode());
    }

    @Test
    void testSingleQuantity() {
        // Given
        LineItem lineItem = new LineItem(
            "Software License",
            1,
            Money.of(10000.00, "THB"),
            new BigDecimal("7.00")
        );

        // When
        Money lineTotal = lineItem.getLineTotal();

        // Then
        assertEquals(Money.of(10000.00, "THB"), lineTotal);
    }

    @Test
    void testLargeQuantity() {
        // Given
        LineItem lineItem = new LineItem(
            "Small Items",
            1000,
            Money.of(5.00, "THB"),
            new BigDecimal("7.00")
        );

        // When
        Money lineTotal = lineItem.getLineTotal();

        // Then
        assertEquals(Money.of(5000.00, "THB"), lineTotal);
    }
}
