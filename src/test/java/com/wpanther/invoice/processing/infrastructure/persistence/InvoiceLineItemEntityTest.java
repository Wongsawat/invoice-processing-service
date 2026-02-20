package com.wpanther.invoice.processing.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvoiceLineItemEntity
 */
class InvoiceLineItemEntityTest {

    @Test
    void testBuilderCreateEntityWithAllFields() {
        // Given/When
        ProcessedInvoiceEntity invoice = ProcessedInvoiceEntity.builder().build();
        InvoiceLineItemEntity lineItem = InvoiceLineItemEntity.builder()
            .invoice(invoice)
            .lineNumber(1)
            .description("Test Item")
            .quantity(10)
            .unitPrice(new BigDecimal("100.00"))
            .taxRate(new BigDecimal("7.00"))
            .lineTotal(new BigDecimal("1000.00"))
            .taxAmount(new BigDecimal("70.00"))
            .build();

        // Then
        assertEquals(invoice, lineItem.getInvoice());
        assertEquals(1, lineItem.getLineNumber());
        assertEquals("Test Item", lineItem.getDescription());
        assertEquals(10, lineItem.getQuantity());
        assertEquals(new BigDecimal("100.00"), lineItem.getUnitPrice());
        assertEquals(new BigDecimal("7.00"), lineItem.getTaxRate());
        assertEquals(new BigDecimal("1000.00"), lineItem.getLineTotal());
        assertEquals(new BigDecimal("70.00"), lineItem.getTaxAmount());
    }

    @Test
    void testSetters() {
        // Given
        InvoiceLineItemEntity lineItem = new InvoiceLineItemEntity();
        ProcessedInvoiceEntity invoice = new ProcessedInvoiceEntity();

        // When
        lineItem.setInvoice(invoice);
        lineItem.setLineNumber(2);
        lineItem.setDescription("Another Item");
        lineItem.setQuantity(5);
        lineItem.setUnitPrice(new BigDecimal("200.00"));
        lineItem.setTaxRate(new BigDecimal("7.00"));
        lineItem.setLineTotal(new BigDecimal("1000.00"));
        lineItem.setTaxAmount(new BigDecimal("70.00"));

        // Then
        assertEquals(invoice, lineItem.getInvoice());
        assertEquals(2, lineItem.getLineNumber());
        assertEquals("Another Item", lineItem.getDescription());
        assertEquals(5, lineItem.getQuantity());
        assertEquals(new BigDecimal("200.00"), lineItem.getUnitPrice());
        assertEquals(new BigDecimal("7.00"), lineItem.getTaxRate());
        assertEquals(new BigDecimal("1000.00"), lineItem.getLineTotal());
        assertEquals(new BigDecimal("70.00"), lineItem.getTaxAmount());
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        ProcessedInvoiceEntity invoice = new ProcessedInvoiceEntity();
        UUID id = UUID.randomUUID();

        // When
        InvoiceLineItemEntity lineItem = new InvoiceLineItemEntity(
            id,
            invoice,
            1,
            "Test Item",
            10,
            new BigDecimal("100.00"),
            new BigDecimal("7.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("70.00")
        );

        // Then
        assertEquals(id, lineItem.getId());
        assertEquals(invoice, lineItem.getInvoice());
        assertEquals(1, lineItem.getLineNumber());
        assertEquals("Test Item", lineItem.getDescription());
        assertEquals(10, lineItem.getQuantity());
    }

    @Test
    void testNoArgsConstructor() {
        // When
        InvoiceLineItemEntity lineItem = new InvoiceLineItemEntity();

        // Then
        assertNotNull(lineItem);
        assertNull(lineItem.getId());
        assertNull(lineItem.getDescription());
    }
}
