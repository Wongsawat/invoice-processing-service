package com.invoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvoiceId value object
 */
class InvoiceIdTest {

    @Test
    void testCreateInvoiceIdWithUUID() {
        // Given
        UUID uuid = UUID.randomUUID();

        // When
        InvoiceId invoiceId = new InvoiceId(uuid);

        // Then
        assertNotNull(invoiceId);
        assertEquals(uuid, invoiceId.value());
    }

    @Test
    void testGenerateInvoiceId() {
        // When
        InvoiceId invoiceId = InvoiceId.generate();

        // Then
        assertNotNull(invoiceId);
        assertNotNull(invoiceId.value());
    }

    @Test
    void testGenerateMultipleInvoiceIds() {
        // When
        InvoiceId id1 = InvoiceId.generate();
        InvoiceId id2 = InvoiceId.generate();

        // Then
        assertNotEquals(id1, id2);
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void testCreateInvoiceIdFromString() {
        // Given
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";

        // When
        InvoiceId invoiceId = InvoiceId.from(uuidString);

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
        InvoiceId id1 = InvoiceId.from(uuidString1);
        InvoiceId id2 = InvoiceId.from(uuidString2);

        // Then
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    @Test
    void testNullUUID() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new InvoiceId(null)
        );
    }

    @Test
    void testFromNullString() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            InvoiceId.from(null)
        );
    }

    @Test
    void testFromInvalidString() {
        // Given
        String invalidUuid = "not-a-valid-uuid";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            InvoiceId.from(invalidUuid)
        );
        assertTrue(exception.getMessage().contains("Invalid invoice ID format"));
    }

    @Test
    void testFromEmptyString() {
        // Given
        String emptyString = "";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            InvoiceId.from(emptyString)
        );
        assertTrue(exception.getMessage().contains("Invalid invoice ID format"));
    }

    @Test
    void testFromMalformedUUID() {
        // Given
        String malformedUuid = "550e8400-e29b-41d4-a716";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            InvoiceId.from(malformedUuid)
        );
        assertTrue(exception.getMessage().contains("Invalid invoice ID format"));
    }

    @Test
    void testToString() {
        // Given
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        InvoiceId invoiceId = InvoiceId.from(uuidString);

        // When
        String result = invoiceId.toString();

        // Then
        assertEquals(uuidString, result);
    }

    @Test
    void testEquality() {
        // Given
        UUID uuid = UUID.randomUUID();
        InvoiceId id1 = new InvoiceId(uuid);
        InvoiceId id2 = new InvoiceId(uuid);
        InvoiceId id3 = new InvoiceId(UUID.randomUUID());

        // When/Then
        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
    }

    @Test
    void testHashCode() {
        // Given
        UUID uuid = UUID.randomUUID();
        InvoiceId id1 = new InvoiceId(uuid);
        InvoiceId id2 = new InvoiceId(uuid);

        // When/Then
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testFromAndToStringRoundTrip() {
        // Given
        InvoiceId original = InvoiceId.generate();
        String stringRepresentation = original.toString();

        // When
        InvoiceId reconstructed = InvoiceId.from(stringRepresentation);

        // Then
        assertEquals(original, reconstructed);
        assertEquals(original.value(), reconstructed.value());
    }

    @Test
    void testSerializability() {
        // Given
        InvoiceId invoiceId = InvoiceId.generate();

        // When/Then
        // InvoiceId should be serializable (implements Serializable through record)
        assertDoesNotThrow(() -> {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
            oos.writeObject(invoiceId);
            oos.close();
        });
    }
}
