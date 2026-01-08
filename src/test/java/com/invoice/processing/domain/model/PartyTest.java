package com.invoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Party value object
 */
class PartyTest {

    @Test
    void testCreatePartyWithAllFields() {
        // Given
        String name = "Acme Corporation Ltd.";
        TaxIdentifier taxId = TaxIdentifier.of("1234567890123", "VAT");
        Address address = new Address("123 Street", "Bangkok", "10110", "TH");
        String email = "info@acme.com";

        // When
        Party party = new Party(name, taxId, address, email);

        // Then
        assertNotNull(party);
        assertEquals(name, party.name());
        assertEquals(taxId, party.taxIdentifier());
        assertEquals(address, party.address());
        assertEquals(email, party.email());
    }

    @Test
    void testCreatePartyWithOfMethodMinimal() {
        // Given
        String name = "Test Company";
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");
        Address address = new Address("Street", "City", "Code", "TH");

        // When
        Party party = Party.of(name, taxId, address);

        // Then
        assertNotNull(party);
        assertEquals(name, party.name());
        assertEquals(taxId, party.taxIdentifier());
        assertEquals(address, party.address());
        assertNull(party.email());
    }

    @Test
    void testCreatePartyWithOfMethodWithEmail() {
        // Given
        String name = "Test Company";
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");
        Address address = new Address("Street", "City", "Code", "TH");
        String email = "test@company.com";

        // When
        Party party = Party.of(name, taxId, address, email);

        // Then
        assertNotNull(party);
        assertEquals(name, party.name());
        assertEquals(email, party.email());
    }

    @Test
    void testNullName() {
        // Given
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");
        Address address = new Address("Street", "City", "Code", "TH");

        // When/Then
        assertThrows(NullPointerException.class, () ->
            new Party(null, taxId, address, null)
        );
    }

    @Test
    void testBlankName() {
        // Given
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");
        Address address = new Address("Street", "City", "Code", "TH");

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            new Party("", taxId, address, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new Party("   ", taxId, address, null)
        );
    }

    @Test
    void testNullTaxIdentifier() {
        // Given
        String name = "Test Company";
        Address address = new Address("Street", "City", "Code", "TH");

        // When
        Party party = new Party(name, null, address, null);

        // Then
        assertNotNull(party);
        assertNull(party.taxIdentifier());
    }

    @Test
    void testNullAddress() {
        // Given
        String name = "Test Company";
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");

        // When
        Party party = new Party(name, taxId, null, null);

        // Then
        assertNotNull(party);
        assertNull(party.address());
    }

    @Test
    void testNullEmail() {
        // Given
        String name = "Test Company";
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");
        Address address = new Address("Street", "City", "Code", "TH");

        // When
        Party party = new Party(name, taxId, address, null);

        // Then
        assertNotNull(party);
        assertNull(party.email());
        assertFalse(party.hasEmail());
    }

    @Test
    void testHasEmailWithValidEmail() {
        // Given
        Party party = Party.of(
            "Test Company",
            TaxIdentifier.of("1234567890"),
            new Address("Street", "City", "Code", "TH"),
            "test@company.com"
        );

        // When/Then
        assertTrue(party.hasEmail());
    }

    @Test
    void testHasEmailWithNullEmail() {
        // Given
        Party party = Party.of(
            "Test Company",
            TaxIdentifier.of("1234567890"),
            new Address("Street", "City", "Code", "TH")
        );

        // When/Then
        assertFalse(party.hasEmail());
    }

    @Test
    void testHasEmailWithBlankEmail() {
        // Given
        Party party = new Party(
            "Test Company",
            TaxIdentifier.of("1234567890"),
            new Address("Street", "City", "Code", "TH"),
            "   "
        );

        // When/Then
        assertFalse(party.hasEmail());
    }

    @Test
    void testEquality() {
        // Given
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");
        Address address = new Address("Street", "City", "Code", "TH");

        Party party1 = new Party("Company A", taxId, address, "email@test.com");
        Party party2 = new Party("Company A", taxId, address, "email@test.com");
        Party party3 = new Party("Company B", taxId, address, "email@test.com");

        // When/Then
        assertEquals(party1, party2);
        assertNotEquals(party1, party3);
    }

    @Test
    void testHashCode() {
        // Given
        TaxIdentifier taxId = TaxIdentifier.of("1234567890");
        Address address = new Address("Street", "City", "Code", "TH");

        Party party1 = new Party("Company A", taxId, address, "email@test.com");
        Party party2 = new Party("Company A", taxId, address, "email@test.com");

        // When/Then
        assertEquals(party1.hashCode(), party2.hashCode());
    }

    @Test
    void testSellerAndBuyerScenario() {
        // Given
        Party seller = Party.of(
            "Seller Company",
            TaxIdentifier.of("1111111111", "VAT"),
            new Address("123 Seller St", "Bangkok", "10110", "TH"),
            "seller@company.com"
        );

        Party buyer = Party.of(
            "Buyer Company",
            TaxIdentifier.of("2222222222", "VAT"),
            new Address("456 Buyer Rd", "Chiang Mai", "50000", "TH"),
            "buyer@company.com"
        );

        // When/Then
        assertNotEquals(seller, buyer);
        assertTrue(seller.hasEmail());
        assertTrue(buyer.hasEmail());
    }
}
