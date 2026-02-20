package com.wpanther.taxinvoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaxInvoiceId value object
 */
class TaxInvoiceIdTest {

    @Test
    void testCreateInvoiceIdWithUUID() {
        // Given
        UUID uuid = UUID.randomUUID();

        // When
        TaxInvoiceId invoiceId = new TaxInvoiceId(uuid);

        // Then
        assertNotNull(invoiceId);
        assertEquals(uuid, invoiceId.value());
    }

    @Test
    void testGenerateInvoiceId() {
        // When
        TaxInvoiceId invoiceId = TaxInvoiceId.generate();

        // Then
        assertNotNull(invoiceId);
        assertNotNull(invoiceId.value());
    }

    @Test
    void testGenerateMultipleInvoiceIds() {
        // When
        TaxInvoiceId id1 = TaxInvoiceId.generate();
        TaxInvoiceId id2 = TaxInvoiceId.generate();

        // Then
        assertNotEquals(id1, id2);
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void testCreateInvoiceIdFromString() {
        // Given
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";

        // When
        TaxInvoiceId invoiceId = TaxInvoiceId.from(uuidString);

        // Then
        assertNotNull(invoiceId);
        assertEquals(UUID.fromString(uuidString), invoiceId.value());
    }

    @Test
    void testCreateInvoiceIdFromStringWithDifferentFormats() {
        // Given
        String uuidString1 = "550e8400-e29b-41d4-a716-446655440000";
        String uuidString2 = UUID.randomUUID().toString();

        // When
        TaxInvoiceId id1 = TaxInvoiceId.from(uuidString1);
        TaxInvoiceId id2 = TaxInvoiceId.from(uuidString2);

        // Then
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    @Test
    void testNullUUID() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new TaxInvoiceId(null)
        );
    }

    @Test
    void testFromNullString() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            TaxInvoiceId.from(null)
        );
    }

    @Test
    void testFromInvalidString() {
        // Given
        String invalidUuid = "not-a-valid-uuid";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            TaxInvoiceId.from(invalidUuid)
        );
        assertTrue(exception.getMessage().contains("Invalid tax invoice ID format"));
    }

    @Test
    void testFromEmptyString() {
        // Given
        String emptyString = "";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            TaxInvoiceId.from(emptyString)
        );
        assertTrue(exception.getMessage().contains("Invalid tax invoice ID format"));
    }

    @Test
    void testFromMalformedUUID() {
        // Given
        String malformedUuid = "550e8400-e29b-41d4-a716";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            TaxInvoiceId.from(malformedUuid)
        );
        assertTrue(exception.getMessage().contains("Invalid tax invoice ID format"));
    }

    @Test
    void testToString() {
        // Given
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        TaxInvoiceId invoiceId = TaxInvoiceId.from(uuidString);

        // When
        String result = invoiceId.toString();

        // Then
        assertEquals(uuidString, result);
    }

    @Test
    void testEquality() {
        // Given
        UUID uuid = UUID.randomUUID();
        TaxInvoiceId id1 = new TaxInvoiceId(uuid);
        TaxInvoiceId id2 = new TaxInvoiceId(uuid);
        TaxInvoiceId id3 = new TaxInvoiceId(UUID.randomUUID());

        // When/Then
        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
    }

    @Test
    void testHashCode() {
        // Given
        UUID uuid = UUID.randomUUID();
        TaxInvoiceId id1 = new TaxInvoiceId(uuid);
        TaxInvoiceId id2 = new TaxInvoiceId(uuid);

        // When/Then
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testFromAndToStringRoundTrip() {
        // Given
        TaxInvoiceId original = TaxInvoiceId.generate();
        String stringRepresentation = original.toString();

        // When
        TaxInvoiceId reconstructed = TaxInvoiceId.from(stringRepresentation);

        // Then
        assertEquals(original, reconstructed);
        assertEquals(original.value(), reconstructed.value());
    }

    @Test
    void testSerializability() {
        // Given
        TaxInvoiceId invoiceId = TaxInvoiceId.generate();

        // When/Then
        // TaxInvoiceId should be serializable (implements Serializable through record)
        assertDoesNotThrow(() -> {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
            oos.writeObject(invoiceId);
            oos.close();
        });
    }
}
