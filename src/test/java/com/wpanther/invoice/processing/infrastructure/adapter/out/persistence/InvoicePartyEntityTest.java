package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.infrastructure.adapter.out.persistence.InvoicePartyEntity.PartyType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvoicePartyEntity
 */
class InvoicePartyEntityTest {

    @Test
    void testBuilderCreateEntityWithAllFields() {
        // Given/When
        ProcessedInvoiceEntity invoice = ProcessedInvoiceEntity.builder().build();
        InvoicePartyEntity party = InvoicePartyEntity.builder()
            .invoice(invoice)
            .partyType(PartyType.SELLER)
            .name("Test Company")
            .taxId("1234567890")
            .taxIdScheme("VAT")
            .streetAddress("123 Street")
            .city("Bangkok")
            .postalCode("10110")
            .country("TH")
            .email("test@example.com")
            .build();

        // Then
        assertEquals(invoice, party.getInvoice());
        assertEquals(PartyType.SELLER, party.getPartyType());
        assertEquals("Test Company", party.getName());
        assertEquals("1234567890", party.getTaxId());
        assertEquals("VAT", party.getTaxIdScheme());
        assertEquals("123 Street", party.getStreetAddress());
        assertEquals("Bangkok", party.getCity());
        assertEquals("10110", party.getPostalCode());
        assertEquals("TH", party.getCountry());
        assertEquals("test@example.com", party.getEmail());
    }

    @Test
    void testSetters() {
        // Given
        InvoicePartyEntity party = new InvoicePartyEntity();
        ProcessedInvoiceEntity invoice = new ProcessedInvoiceEntity();

        // When
        party.setInvoice(invoice);
        party.setPartyType(PartyType.BUYER);
        party.setName("Buyer Company");
        party.setTaxId("9876543210");
        party.setTaxIdScheme("VAT");
        party.setStreetAddress("456 Road");
        party.setCity("Chiang Mai");
        party.setPostalCode("50000");
        party.setCountry("TH");
        party.setEmail("buyer@example.com");

        // Then
        assertEquals(invoice, party.getInvoice());
        assertEquals(PartyType.BUYER, party.getPartyType());
        assertEquals("Buyer Company", party.getName());
        assertEquals("9876543210", party.getTaxId());
        assertEquals("VAT", party.getTaxIdScheme());
        assertEquals("456 Road", party.getStreetAddress());
        assertEquals("Chiang Mai", party.getCity());
        assertEquals("50000", party.getPostalCode());
        assertEquals("TH", party.getCountry());
        assertEquals("buyer@example.com", party.getEmail());
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        ProcessedInvoiceEntity invoice = new ProcessedInvoiceEntity();
        UUID id = UUID.randomUUID();

        // When
        InvoicePartyEntity party = new InvoicePartyEntity(
            id,
            invoice,
            PartyType.SELLER,
            "Test Company",
            "1234567890",
            "VAT",
            "123 Street",
            "Bangkok",
            "10110",
            "TH",
            "test@example.com"
        );

        // Then
        assertEquals(id, party.getId());
        assertEquals(invoice, party.getInvoice());
        assertEquals(PartyType.SELLER, party.getPartyType());
        assertEquals("Test Company", party.getName());
    }

    @Test
    void testNoArgsConstructor() {
        // When
        InvoicePartyEntity party = new InvoicePartyEntity();

        // Then
        assertNotNull(party);
        assertNull(party.getId());
        assertNull(party.getName());
        assertNull(party.getPartyType());
    }

    @Test
    void testPartyTypeEnum() {
        // Then
        assertEquals(PartyType.SELLER, PartyType.valueOf("SELLER"));
        assertEquals(PartyType.BUYER, PartyType.valueOf("BUYER"));
        assertEquals(2, PartyType.values().length);
    }
}
