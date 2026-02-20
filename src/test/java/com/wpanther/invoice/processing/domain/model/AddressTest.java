package com.wpanther.invoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Address value object
 */
class AddressTest {

    @Test
    void testCreateAddressWithAllFields() {
        // Given
        String streetAddress = "123 Business Street";
        String city = "Bangkok";
        String postalCode = "10110";
        String country = "TH";

        // When
        Address address = new Address(streetAddress, city, postalCode, country);

        // Then
        assertNotNull(address);
        assertEquals(streetAddress, address.streetAddress());
        assertEquals(city, address.city());
        assertEquals(postalCode, address.postalCode());
        assertEquals(country, address.country());
    }

    @Test
    void testCreateAddressWithOfMethod() {
        // When
        Address address = Address.of("456 Street", "City", "12345", "US");

        // Then
        assertNotNull(address);
        assertEquals("456 Street", address.streetAddress());
        assertEquals("City", address.city());
        assertEquals("12345", address.postalCode());
        assertEquals("US", address.country());
    }

    @Test
    void testCreateAddressWithNullStreetAddress() {
        // When
        Address address = new Address(null, "Bangkok", "10110", "TH");

        // Then
        assertNotNull(address);
        assertNull(address.streetAddress());
    }

    @Test
    void testCreateAddressWithNullCity() {
        // When
        Address address = new Address("123 Street", null, "10110", "TH");

        // Then
        assertNotNull(address);
        assertNull(address.city());
    }

    @Test
    void testCreateAddressWithNullPostalCode() {
        // When
        Address address = new Address("123 Street", "Bangkok", null, "TH");

        // Then
        assertNotNull(address);
        assertNull(address.postalCode());
    }

    @Test
    void testNullCountry() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new Address("123 Street", "Bangkok", "10110", null)
        );
    }

    @Test
    void testBlankCountry() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new Address("123 Street", "Bangkok", "10110", "")
        );

        assertThrows(IllegalArgumentException.class, () ->
            new Address("123 Street", "Bangkok", "10110", "   ")
        );
    }

    @Test
    void testToSingleLineWithAllFields() {
        // Given
        Address address = new Address("123 Business Street", "Bangkok", "10110", "TH");

        // When
        String result = address.toSingleLine();

        // Then
        assertEquals("123 Business Street, Bangkok 10110, TH", result);
    }

    @Test
    void testToSingleLineWithoutStreetAddress() {
        // Given
        Address address = new Address(null, "Bangkok", "10110", "TH");

        // When
        String result = address.toSingleLine();

        // Then
        assertEquals("Bangkok 10110, TH", result);
    }

    @Test
    void testToSingleLineWithoutCity() {
        // Given
        Address address = new Address("123 Street", null, "10110", "TH");

        // When
        String result = address.toSingleLine();

        // Then
        assertEquals("123 Street 10110, TH", result);
    }

    @Test
    void testToSingleLineWithoutPostalCode() {
        // Given
        Address address = new Address("123 Street", "Bangkok", null, "TH");

        // When
        String result = address.toSingleLine();

        // Then
        assertEquals("123 Street, Bangkok, TH", result);
    }

    @Test
    void testToSingleLineWithOnlyCountry() {
        // Given
        Address address = new Address(null, null, null, "TH");

        // When
        String result = address.toSingleLine();

        // Then
        assertEquals("TH", result);
    }

    @Test
    void testToSingleLineWithBlankFields() {
        // Given
        Address address = new Address("", "  ", "", "TH");

        // When
        String result = address.toSingleLine();

        // Then
        assertEquals("TH", result);
    }

    @Test
    void testEquality() {
        // Given
        Address address1 = new Address("123 Street", "Bangkok", "10110", "TH");
        Address address2 = new Address("123 Street", "Bangkok", "10110", "TH");
        Address address3 = new Address("456 Street", "Bangkok", "10110", "TH");

        // When/Then
        assertEquals(address1, address2);
        assertNotEquals(address1, address3);
    }

    @Test
    void testHashCode() {
        // Given
        Address address1 = new Address("123 Street", "Bangkok", "10110", "TH");
        Address address2 = new Address("123 Street", "Bangkok", "10110", "TH");

        // When/Then
        assertEquals(address1.hashCode(), address2.hashCode());
    }

    @Test
    void testDifferentCountries() {
        // Given
        Address thaiAddress = new Address("123 Street", "Bangkok", "10110", "TH");
        Address usAddress = new Address("456 Avenue", "New York", "10001", "US");

        // When/Then
        assertNotEquals(thaiAddress, usAddress);
    }
}
