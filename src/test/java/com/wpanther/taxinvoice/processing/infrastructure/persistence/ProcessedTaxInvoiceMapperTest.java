package com.wpanther.taxinvoice.processing.infrastructure.persistence;

import com.wpanther.taxinvoice.processing.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessedTaxInvoiceMapper
 */
class ProcessedTaxInvoiceMapperTest {

    private ProcessedTaxInvoiceMapper mapper;
    private ProcessedTaxInvoice domainInvoice;

    @BeforeEach
    void setUp() {
        mapper = new ProcessedTaxInvoiceMapper();

        Party seller = Party.of(
            "Seller Company",
            TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 Street", "Bangkok", "10110", "TH"),
            "seller@company.com"
        );

        Party buyer = Party.of(
            "Buyer Company",
            TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Road", "Chiang Mai", "50000", "TH"),
            "buyer@company.com"
        );

        LineItem item1 = new LineItem(
            "Service 1",
            10,
            Money.of(1000.00, "THB"),
            new BigDecimal("7.00")
        );

        LineItem item2 = new LineItem(
            "Service 2",
            5,
            Money.of(2000.00, "THB"),
            new BigDecimal("7.00")
        );

        domainInvoice = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(seller)
            .buyer(buyer)
            .addItem(item1)
            .addItem(item2)
            .currency("THB")
            .originalXml("<xml>test</xml>")
            .status(ProcessingStatus.PENDING)
            .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
            .build();
    }

    @Test
    void testToEntity() {
        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // Then
        assertNotNull(entity);
        assertEquals(domainInvoice.getId().value(), entity.getId());
        assertEquals("intake-123", entity.getSourceInvoiceId());
        assertEquals("INV-001", entity.getInvoiceNumber());
        assertEquals(LocalDate.of(2025, 1, 1), entity.getIssueDate());
        assertEquals(LocalDate.of(2025, 2, 1), entity.getDueDate());
        assertEquals("THB", entity.getCurrency());
        assertEquals(ProcessingStatus.PENDING, entity.getStatus());
        assertEquals("<xml>test</xml>", entity.getOriginalXml());
    }

    @Test
    void testToEntityCalculatedTotals() {
        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // Then
        // Subtotal: (10 * 1000) + (5 * 2000) = 20,000
        assertEquals(new BigDecimal("20000.00"), entity.getSubtotal());
        // Tax: 20,000 * 0.07 = 1,400
        assertEquals(new BigDecimal("1400.00"), entity.getTotalTax());
        // Total: 20,000 + 1,400 = 21,400
        assertEquals(new BigDecimal("21400.00"), entity.getTotal());
    }

    @Test
    void testToEntityParties() {
        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // Then
        assertNotNull(entity.getParties());
        assertEquals(2, entity.getParties().size());

        // Find seller and buyer
        TaxInvoicePartyEntity seller = entity.getParties().stream()
            .filter(p -> p.getPartyType() == TaxInvoicePartyEntity.PartyType.SELLER)
            .findFirst()
            .orElse(null);

        TaxInvoicePartyEntity buyer = entity.getParties().stream()
            .filter(p -> p.getPartyType() == TaxInvoicePartyEntity.PartyType.BUYER)
            .findFirst()
            .orElse(null);

        assertNotNull(seller);
        assertEquals("Seller Company", seller.getName());
        assertEquals("1234567890", seller.getTaxId());
        assertEquals("VAT", seller.getTaxIdScheme());
        assertEquals("123 Street", seller.getStreetAddress());
        assertEquals("Bangkok", seller.getCity());
        assertEquals("10110", seller.getPostalCode());
        assertEquals("TH", seller.getCountry());
        assertEquals("seller@company.com", seller.getEmail());

        assertNotNull(buyer);
        assertEquals("Buyer Company", buyer.getName());
        assertEquals("9876543210", buyer.getTaxId());
    }

    @Test
    void testToEntityLineItems() {
        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // Then
        assertNotNull(entity.getLineItems());
        assertEquals(2, entity.getLineItems().size());

        TaxInvoiceLineItemEntity item1 = entity.getLineItems().get(0);
        assertEquals(1, item1.getLineNumber());
        assertEquals("Service 1", item1.getDescription());
        assertEquals(10, item1.getQuantity());
        assertEquals(new BigDecimal("1000.00"), item1.getUnitPrice());
        assertEquals(new BigDecimal("7.00"), item1.getTaxRate());
        assertEquals(new BigDecimal("10000.00"), item1.getLineTotal());
        assertEquals(new BigDecimal("700.00"), item1.getTaxAmount());

        TaxInvoiceLineItemEntity item2 = entity.getLineItems().get(1);
        assertEquals(2, item2.getLineNumber());
        assertEquals("Service 2", item2.getDescription());
    }

    @Test
    void testToDomain() {
        // Given
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // When
        ProcessedTaxInvoice reconstructed = mapper.toDomain(entity);

        // Then
        assertNotNull(reconstructed);
        assertEquals(domainInvoice.getId(), reconstructed.getId());
        assertEquals(domainInvoice.getSourceInvoiceId(), reconstructed.getSourceInvoiceId());
        assertEquals(domainInvoice.getInvoiceNumber(), reconstructed.getInvoiceNumber());
        assertEquals(domainInvoice.getIssueDate(), reconstructed.getIssueDate());
        assertEquals(domainInvoice.getDueDate(), reconstructed.getDueDate());
        assertEquals(domainInvoice.getCurrency(), reconstructed.getCurrency());
        assertEquals(domainInvoice.getStatus(), reconstructed.getStatus());
        assertEquals(domainInvoice.getOriginalXml(), reconstructed.getOriginalXml());
    }

    @Test
    void testToDomainParties() {
        // Given
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // When
        ProcessedTaxInvoice reconstructed = mapper.toDomain(entity);

        // Then
        assertNotNull(reconstructed.getSeller());
        assertEquals("Seller Company", reconstructed.getSeller().name());
        assertEquals("1234567890", reconstructed.getSeller().taxIdentifier().value());
        assertEquals("123 Street", reconstructed.getSeller().address().streetAddress());
        assertEquals("seller@company.com", reconstructed.getSeller().email());

        assertNotNull(reconstructed.getBuyer());
        assertEquals("Buyer Company", reconstructed.getBuyer().name());
        assertEquals("9876543210", reconstructed.getBuyer().taxIdentifier().value());
    }

    @Test
    void testToDomainLineItems() {
        // Given
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // When
        ProcessedTaxInvoice reconstructed = mapper.toDomain(entity);

        // Then
        assertNotNull(reconstructed.getItems());
        assertEquals(2, reconstructed.getItems().size());

        LineItem item1 = reconstructed.getItems().get(0);
        assertEquals("Service 1", item1.description());
        assertEquals(10, item1.quantity());
        assertEquals(Money.of(1000.00, "THB"), item1.unitPrice());
        assertEquals(new BigDecimal("7.00"), item1.taxRate());

        LineItem item2 = reconstructed.getItems().get(1);
        assertEquals("Service 2", item2.description());
        assertEquals(5, item2.quantity());
    }

    @Test
    void testRoundTripConversion() {
        // Given
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // When
        ProcessedTaxInvoice reconstructed = mapper.toDomain(entity);

        // Then - Totals should match
        assertEquals(domainInvoice.getSubtotal(), reconstructed.getSubtotal());
        assertEquals(domainInvoice.getTotalTax(), reconstructed.getTotalTax());
        assertEquals(domainInvoice.getTotal(), reconstructed.getTotal());
    }

    @Test
    void testToEntityWithNullTaxIdentifier() {
        // Given
        Party sellerWithoutTaxId = Party.of(
            "Seller",
            null,
            new Address("Street", "City", "Code", "TH")
        );

        ProcessedTaxInvoice invoice = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(sellerWithoutTaxId)
            .buyer(domainInvoice.getBuyer())
            .items(domainInvoice.getItems())
            .currency("THB")
            .originalXml("<xml>test</xml>")
            .build();

        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(invoice);

        // Then
        TaxInvoicePartyEntity sellerEntity = entity.getParties().stream()
            .filter(p -> p.getPartyType() == TaxInvoicePartyEntity.PartyType.SELLER)
            .findFirst()
            .orElse(null);

        assertNotNull(sellerEntity);
        assertNull(sellerEntity.getTaxId());
        assertNull(sellerEntity.getTaxIdScheme());
    }

    @Test
    void testToEntityWithNullAddress() {
        // Given
        Party sellerWithoutAddress = Party.of(
            "Seller",
            TaxIdentifier.of("1234567890"),
            null
        );

        ProcessedTaxInvoice invoice = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(sellerWithoutAddress)
            .buyer(domainInvoice.getBuyer())
            .items(domainInvoice.getItems())
            .currency("THB")
            .originalXml("<xml>test</xml>")
            .build();

        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(invoice);

        // Then
        TaxInvoicePartyEntity sellerEntity = entity.getParties().stream()
            .filter(p -> p.getPartyType() == TaxInvoicePartyEntity.PartyType.SELLER)
            .findFirst()
            .orElse(null);

        assertNotNull(sellerEntity);
        assertNull(sellerEntity.getStreetAddress());
        assertNull(sellerEntity.getCity());
        assertNull(sellerEntity.getPostalCode());
        assertEquals("UNKNOWN", sellerEntity.getCountry());
    }

    @Test
    void testToEntityWithCompletedStatus() {
        // Given
        ProcessedTaxInvoice completedInvoice = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(domainInvoice.getSeller())
            .buyer(domainInvoice.getBuyer())
            .items(domainInvoice.getItems())
            .currency("THB")
            .originalXml("<xml>test</xml>")
            .status(ProcessingStatus.COMPLETED)
            .completedAt(LocalDateTime.of(2025, 1, 1, 12, 0))
            .build();

        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(completedInvoice);

        // Then
        assertEquals(ProcessingStatus.COMPLETED, entity.getStatus());
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0), entity.getCompletedAt());
    }

    @Test
    void testToEntityWithErrorMessage() {
        // Given
        ProcessedTaxInvoice failedInvoice = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(domainInvoice.getSeller())
            .buyer(domainInvoice.getBuyer())
            .items(domainInvoice.getItems())
            .currency("THB")
            .originalXml("<xml>test</xml>")
            .status(ProcessingStatus.FAILED)
            .errorMessage("Test error message")
            .build();

        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(failedInvoice);

        // Then
        assertEquals(ProcessingStatus.FAILED, entity.getStatus());
        assertEquals("Test error message", entity.getErrorMessage());
    }

    @Test
    void testToDomainReconstructsCorrectly() {
        // Given - Create entity, convert to domain, then back to entity
        ProcessedTaxInvoiceEntity entity1 = mapper.toEntity(domainInvoice);
        ProcessedTaxInvoice domain = mapper.toDomain(entity1);
        ProcessedTaxInvoiceEntity entity2 = mapper.toEntity(domain);

        // Then - Both entities should have same data
        assertEquals(entity1.getId(), entity2.getId());
        assertEquals(entity1.getInvoiceNumber(), entity2.getInvoiceNumber());
        assertEquals(entity1.getSubtotal(), entity2.getSubtotal());
        assertEquals(entity1.getTotalTax(), entity2.getTotalTax());
        assertEquals(entity1.getTotal(), entity2.getTotal());
    }

    @Test
    void testLineItemNumbering() {
        // When
        ProcessedTaxInvoiceEntity entity = mapper.toEntity(domainInvoice);

        // Then - Line numbers should be sequential starting from 1
        List<TaxInvoiceLineItemEntity> items = entity.getLineItems();
        for (int i = 0; i < items.size(); i++) {
            assertEquals(i + 1, items.get(i).getLineNumber());
        }
    }
}
