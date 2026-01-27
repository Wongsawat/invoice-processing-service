package com.wpanther.invoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaxIdentifier value object
 */
class TaxIdentifierTest {

    @Test
    void testCreateTaxIdentifierWithBothParameters() {
        // Given
        String value = "1234567890123";
        String scheme = "VAT";

        // When
        TaxIdentifier taxId = new TaxIdentifier(value, scheme);

        // Then
        assertNotNull(taxId);
        assertEquals(value, taxId.value());
        assertEquals(scheme, taxId.scheme());
    }

    @Test
    void testCreateTaxIdentifierWithOfMethodDefaultScheme() {
        // Given
        String value = "1234567890123";

        // When
        TaxIdentifier taxId = TaxIdentifier.of(value);

        // Then
        assertNotNull(taxId);
        assertEquals(value, taxId.value());
        assertEquals("VAT", taxId.scheme());
    }

    @Test
    void testCreateTaxIdentifierWithOfMethodCustomScheme() {
        // Given
        String value = "1234567890123";
        String scheme = "EIN";

        // When
        TaxIdentifier taxId = TaxIdentifier.of(value, scheme);

        // Then
        assertNotNull(taxId);
        assertEquals(value, taxId.value());
        assertEquals(scheme, taxId.scheme());
    }

    @Test
    void testNullValue() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new TaxIdentifier(null, "VAT")
        );
    }

    @Test
    void testBlankValue() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new TaxIdentifier("", "VAT")
        );

        assertThrows(IllegalArgumentException.class, () ->
            new TaxIdentifier("   ", "VAT")
        );
    }

    @Test
    void testNullScheme() {
        // Given
        String value = "1234567890123";

        // When
        TaxIdentifier taxId = new TaxIdentifier(value, null);

        // Then
        assertNotNull(taxId);
        assertEquals(value, taxId.value());
        assertNull(taxId.scheme());
    }

    @Test
    void testToStringWithScheme() {
        // Given
        TaxIdentifier taxId = TaxIdentifier.of("1234567890123", "VAT");

        // When
        String result = taxId.toString();

        // Then
        assertEquals("VAT:1234567890123", result);
    }

    @Test
    void testToStringWithNullScheme() {
        // Given
        TaxIdentifier taxId = new TaxIdentifier("1234567890123", null);

        // When
        String result = taxId.toString();

        // Then
        assertEquals("1234567890123", result);
    }

    @Test
    void testToStringWithBlankScheme() {
        // Given
        TaxIdentifier taxId = new TaxIdentifier("1234567890123", "");

        // When
        String result = taxId.toString();

        // Then
        assertEquals("1234567890123", result);
    }

    @Test
    void testEquality() {
        // Given
        TaxIdentifier taxId1 = TaxIdentifier.of("1234567890123", "VAT");
        TaxIdentifier taxId2 = TaxIdentifier.of("1234567890123", "VAT");
        TaxIdentifier taxId3 = TaxIdentifier.of("9876543210987", "VAT");

        // When/Then
        assertEquals(taxId1, taxId2);
        assertNotEquals(taxId1, taxId3);
    }

    @Test
    void testEqualityDifferentSchemes() {
        // Given
        TaxIdentifier taxId1 = TaxIdentifier.of("1234567890123", "VAT");
        TaxIdentifier taxId2 = TaxIdentifier.of("1234567890123", "EIN");

        // When/Then
        assertNotEquals(taxId1, taxId2);
    }

    @Test
    void testHashCode() {
        // Given
        TaxIdentifier taxId1 = TaxIdentifier.of("1234567890123", "VAT");
        TaxIdentifier taxId2 = TaxIdentifier.of("1234567890123", "VAT");

        // When/Then
        assertEquals(taxId1.hashCode(), taxId2.hashCode());
    }

    @Test
    void testDifferentSchemes() {
        // Given
        TaxIdentifier vatId = TaxIdentifier.of("1234567890", "VAT");
        TaxIdentifier einId = TaxIdentifier.of("9876543210", "EIN");
        TaxIdentifier tinId = TaxIdentifier.of("5555555555", "TIN");

        // When/Then
        assertNotEquals(vatId, einId);
        assertNotEquals(vatId, tinId);
        assertNotEquals(einId, tinId);
    }
}
