package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessedInvoiceEntity
 */
class ProcessedInvoiceEntityTest {

    @Test
    void testBuilderCreateEntityWithAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        String sourceId = "source-123";
        String invoiceNumber = "INV-001";
        LocalDate issueDate = LocalDate.of(2025, 1, 1);
        LocalDate dueDate = LocalDate.of(2025, 2, 1);
        String currency = "THB";
        BigDecimal subtotal = new BigDecimal("1000.00");
        BigDecimal totalTax = new BigDecimal("70.00");
        BigDecimal total = new BigDecimal("1070.00");
        String originalXml = "<xml>test</xml>";
        ProcessingStatus status = ProcessingStatus.COMPLETED;
        String errorMessage = "Test error";
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime completedAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        ProcessedInvoiceEntity entity = ProcessedInvoiceEntity.builder()
            .id(id)
            .sourceInvoiceId(sourceId)
            .invoiceNumber(invoiceNumber)
            .issueDate(issueDate)
            .dueDate(dueDate)
            .currency(currency)
            .subtotal(subtotal)
            .totalTax(totalTax)
            .total(total)
            .originalXml(originalXml)
            .status(status)
            .errorMessage(errorMessage)
            .createdAt(createdAt)
            .completedAt(completedAt)
            .updatedAt(updatedAt)
            .parties(new HashSet<>())
            .lineItems(new ArrayList<>())
            .build();

        // Then
        assertEquals(id, entity.getId());
        assertEquals(sourceId, entity.getSourceInvoiceId());
        assertEquals(invoiceNumber, entity.getInvoiceNumber());
        assertEquals(issueDate, entity.getIssueDate());
        assertEquals(dueDate, entity.getDueDate());
        assertEquals(currency, entity.getCurrency());
        assertEquals(subtotal, entity.getSubtotal());
        assertEquals(totalTax, entity.getTotalTax());
        assertEquals(total, entity.getTotal());
        assertEquals(originalXml, entity.getOriginalXml());
        assertEquals(status, entity.getStatus());
        assertEquals(errorMessage, entity.getErrorMessage());
        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(completedAt, entity.getCompletedAt());
        assertEquals(updatedAt, entity.getUpdatedAt());
        assertNotNull(entity.getParties());
        assertNotNull(entity.getLineItems());
    }

    @Test
    void testSetters() {
        // Given
        ProcessedInvoiceEntity entity = new ProcessedInvoiceEntity();
        UUID id = UUID.randomUUID();
        String sourceId = "source-123";

        // When
        entity.setId(id);
        entity.setSourceInvoiceId(sourceId);
        entity.setInvoiceNumber("INV-001");
        entity.setIssueDate(LocalDate.of(2025, 1, 1));
        entity.setDueDate(LocalDate.of(2025, 2, 1));
        entity.setCurrency("THB");
        entity.setSubtotal(new BigDecimal("1000.00"));
        entity.setTotalTax(new BigDecimal("70.00"));
        entity.setTotal(new BigDecimal("1070.00"));
        entity.setOriginalXml("<xml>test</xml>");
        entity.setStatus(ProcessingStatus.COMPLETED);
        entity.setErrorMessage("Error");
        entity.setParties(new HashSet<>());
        entity.setLineItems(new ArrayList<>());

        // Then
        assertEquals(id, entity.getId());
        assertEquals(sourceId, entity.getSourceInvoiceId());
        assertEquals("INV-001", entity.getInvoiceNumber());
        assertNotNull(entity.getIssueDate());
        assertNotNull(entity.getDueDate());
        assertEquals("THB", entity.getCurrency());
        assertEquals(ProcessingStatus.COMPLETED, entity.getStatus());
        assertEquals("Error", entity.getErrorMessage());
    }

    @Test
    void testAddParty() {
        // Given
        ProcessedInvoiceEntity entity = ProcessedInvoiceEntity.builder()
            .parties(new HashSet<>())
            .build();
        InvoicePartyEntity party = InvoicePartyEntity.builder()
            .partyType(InvoicePartyEntity.PartyType.SELLER)
            .name("Test Company")
            .build();

        // When
        entity.addParty(party);

        // Then
        assertEquals(1, entity.getParties().size());
        assertEquals(entity, party.getInvoice());
    }

    @Test
    void testAddLineItem() {
        // Given
        ProcessedInvoiceEntity entity = ProcessedInvoiceEntity.builder()
            .lineItems(new ArrayList<>())
            .build();
        InvoiceLineItemEntity lineItem = InvoiceLineItemEntity.builder()
            .lineNumber(1)
            .description("Test item")
            .build();

        // When
        entity.addLineItem(lineItem);

        // Then
        assertEquals(1, entity.getLineItems().size());
        assertEquals(entity, lineItem.getInvoice());
    }

    @Test
    void testPrePersistGeneratesId() {
        // Given
        ProcessedInvoiceEntity entity = new ProcessedInvoiceEntity();
        assertNull(entity.getId());

        // When
        entity.onCreate();

        // Then
        assertNotNull(entity.getId(), "ID should be generated on create");
    }

    @Test
    void testPrePersistDoesNotOverrideExistingId() {
        // Given
        UUID existingId = UUID.randomUUID();
        ProcessedInvoiceEntity entity = new ProcessedInvoiceEntity();
        entity.setId(existingId);

        // When
        entity.onCreate();

        // Then
        assertEquals(existingId, entity.getId(), "Existing ID should not be overridden");
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // When
        ProcessedInvoiceEntity entity = new ProcessedInvoiceEntity(
            id,
            "source-123",
            "INV-001",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 2, 1),
            "THB",
            new BigDecimal("1000.00"),
            new BigDecimal("70.00"),
            new BigDecimal("1070.00"),
            "<xml>test</xml>",
            ProcessingStatus.PENDING,
            null,
            now,
            null,
            now,
            0L,
            new HashSet<>(),
            new ArrayList<>()
        );

        // Then
        assertEquals(id, entity.getId());
        assertEquals("source-123", entity.getSourceInvoiceId());
        assertEquals("INV-001", entity.getInvoiceNumber());
    }

    @Test
    void testNoArgsConstructor() {
        // When
        ProcessedInvoiceEntity entity = new ProcessedInvoiceEntity();

        // Then
        assertNotNull(entity);
        assertNull(entity.getId());
        assertNull(entity.getInvoiceNumber());
    }
}
