package com.wpanther.taxinvoice.processing.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessedTaxInvoice aggregate root
 */
class ProcessedTaxInvoiceTest {

    private ProcessedTaxInvoice.Builder validInvoiceBuilder;

    @BeforeEach
    void setUp() {
        Party seller = Party.of(
            "Acme Corporation",
            TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 Street", "Bangkok", "10110", "TH")
        );

        Party buyer = Party.of(
            "Customer Company",
            TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Road", "Chiang Mai", "50000", "TH")
        );

        LineItem item1 = new LineItem(
            "Service 1",
            10,
            Money.of(1000.00, "THB"),
            new BigDecimal("7.00")
        );

        validInvoiceBuilder = ProcessedTaxInvoice.builder()
            .id(TaxInvoiceId.generate())
            .sourceInvoiceId("intake-123")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(seller)
            .buyer(buyer)
            .addItem(item1)
            .currency("THB")
            .originalXml("<xml>test</xml>");
    }

    @Test
    void testCreateValidInvoice() {
        // When
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // Then
        assertNotNull(invoice);
        assertEquals("intake-123", invoice.getSourceInvoiceId());
        assertEquals("INV-001", invoice.getInvoiceNumber());
        assertEquals(LocalDate.of(2025, 1, 1), invoice.getIssueDate());
        assertEquals(LocalDate.of(2025, 2, 1), invoice.getDueDate());
        assertEquals("THB", invoice.getCurrency());
        assertEquals(ProcessingStatus.PENDING, invoice.getStatus());
        assertNotNull(invoice.getCreatedAt());
    }

    @Test
    void testCalculateSubtotal() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When
        Money subtotal = invoice.getSubtotal();

        // Then
        // 10 * 1000 = 10,000
        assertEquals(Money.of(10000.00, "THB"), subtotal);
    }

    @Test
    void testCalculateTotalTax() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When
        Money totalTax = invoice.getTotalTax();

        // Then
        // 10,000 * 0.07 = 700
        assertEquals(Money.of(700.00, "THB"), totalTax);
    }

    @Test
    void testCalculateTotal() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When
        Money total = invoice.getTotal();

        // Then
        // 10,000 + 700 = 10,700
        assertEquals(Money.of(10700.00, "THB"), total);
    }

    @Test
    void testCalculateTotalsWithMultipleItems() {
        // Given
        LineItem item2 = new LineItem(
            "Service 2",
            5,
            Money.of(2000.00, "THB"),
            new BigDecimal("7.00")
        );

        ProcessedTaxInvoice invoice = validInvoiceBuilder
            .addItem(item2)
            .build();

        // When
        Money subtotal = invoice.getSubtotal();
        Money totalTax = invoice.getTotalTax();
        Money total = invoice.getTotal();

        // Then
        // Subtotal: (10 * 1000) + (5 * 2000) = 20,000
        assertEquals(Money.of(20000.00, "THB"), subtotal);
        // Tax: 20,000 * 0.07 = 1,400
        assertEquals(Money.of(1400.00, "THB"), totalTax);
        // Total: 20,000 + 1,400 = 21,400
        assertEquals(Money.of(21400.00, "THB"), total);
    }

    @Test
    void testStartProcessing() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();
        assertEquals(ProcessingStatus.PENDING, invoice.getStatus());

        // When
        invoice.startProcessing();

        // Then
        assertEquals(ProcessingStatus.PROCESSING, invoice.getStatus());
    }

    @Test
    void testStartProcessingFromNonPendingStatus() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();
        invoice.startProcessing();

        // When/Then
        assertThrows(IllegalStateException.class, invoice::startProcessing);
    }

    @Test
    void testMarkCompleted() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();
        invoice.startProcessing();

        // When
        invoice.markCompleted();

        // Then
        assertEquals(ProcessingStatus.COMPLETED, invoice.getStatus());
        assertNotNull(invoice.getCompletedAt());
    }

    @Test
    void testMarkCompletedFromNonProcessingStatus() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When/Then
        assertThrows(IllegalStateException.class, invoice::markCompleted);
    }

    @Test
    void testMarkFailed() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();
        String errorMessage = "Processing failed due to invalid data";

        // When
        invoice.markFailed(errorMessage);

        // Then
        assertEquals(ProcessingStatus.FAILED, invoice.getStatus());
        assertEquals(errorMessage, invoice.getErrorMessage());
        assertNotNull(invoice.getCompletedAt());
    }

    @Test
    void testRequestPdfGeneration() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();
        invoice.startProcessing();
        invoice.markCompleted();

        // When
        invoice.requestPdfGeneration();

        // Then
        assertEquals(ProcessingStatus.PDF_REQUESTED, invoice.getStatus());
    }

    @Test
    void testRequestPdfGenerationFromNonCompletedStatus() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When/Then
        assertThrows(IllegalStateException.class, invoice::requestPdfGeneration);
    }

    @Test
    void testMarkPdfGenerated() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();
        invoice.startProcessing();
        invoice.markCompleted();
        invoice.requestPdfGeneration();

        // When
        invoice.markPdfGenerated();

        // Then
        assertEquals(ProcessingStatus.PDF_GENERATED, invoice.getStatus());
    }

    @Test
    void testMarkPdfGeneratedFromNonPdfRequestedStatus() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When/Then
        assertThrows(IllegalStateException.class, invoice::markPdfGenerated);
    }

    @Test
    void testNullId() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.id(null).build()
        );
    }

    @Test
    void testNullSourceInvoiceId() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.sourceInvoiceId(null).build()
        );
    }

    @Test
    void testNullInvoiceNumber() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.invoiceNumber(null).build()
        );
    }

    @Test
    void testNullIssueDate() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.issueDate(null).build()
        );
    }

    @Test
    void testNullDueDate() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.dueDate(null).build()
        );
    }

    @Test
    void testNullSeller() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.seller(null).build()
        );
    }

    @Test
    void testNullBuyer() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.buyer(null).build()
        );
    }

    @Test
    void testNullItems() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.items(null).build()
        );
    }

    @Test
    void testEmptyItems() {
        // When/Then
        assertThrows(IllegalStateException.class, () ->
            validInvoiceBuilder.items(new ArrayList<>()).build()
        );
    }

    @Test
    void testNullCurrency() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.currency(null).build()
        );
    }

    @Test
    void testNullOriginalXml() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            validInvoiceBuilder.originalXml(null).build()
        );
    }

    @Test
    void testInvalidCurrencyLength() {
        // When/Then
        assertThrows(IllegalStateException.class, () ->
            validInvoiceBuilder.currency("US").build()
        );

        assertThrows(IllegalStateException.class, () ->
            validInvoiceBuilder.currency("USDT").build()
        );
    }

    @Test
    void testDueDateBeforeIssueDate() {
        // When/Then
        assertThrows(IllegalStateException.class, () ->
            validInvoiceBuilder
                .issueDate(LocalDate.of(2025, 2, 1))
                .dueDate(LocalDate.of(2025, 1, 1))
                .build()
        );
    }

    @Test
    void testLineItemCurrencyMismatch() {
        // Given
        LineItem itemWithDifferentCurrency = new LineItem(
            "Service",
            10,
            Money.of(1000.00, "USD"),  // Different currency
            new BigDecimal("7.00")
        );

        // When/Then
        assertThrows(IllegalStateException.class, () ->
            validInvoiceBuilder
                .items(List.of(itemWithDifferentCurrency))
                .build()
        );
    }

    @Test
    void testGetItemsReturnsUnmodifiableList() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When
        List<LineItem> items = invoice.getItems();

        // Then
        assertThrows(UnsupportedOperationException.class, () ->
            items.add(new LineItem("New Item", 1, Money.of(100.00, "THB"), BigDecimal.ZERO))
        );
    }

    @Test
    void testBuilderWithCustomStatus() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder
            .status(ProcessingStatus.PROCESSING)
            .build();

        // When/Then
        assertEquals(ProcessingStatus.PROCESSING, invoice.getStatus());
    }

    @Test
    void testBuilderWithCustomCreatedAt() {
        // Given
        LocalDateTime customCreatedAt = LocalDateTime.of(2025, 1, 1, 10, 0);
        ProcessedTaxInvoice invoice = validInvoiceBuilder
            .createdAt(customCreatedAt)
            .build();

        // When/Then
        assertEquals(customCreatedAt, invoice.getCreatedAt());
    }

    @Test
    void testBuilderWithCompletedAt() {
        // Given
        LocalDateTime completedAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        ProcessedTaxInvoice invoice = validInvoiceBuilder
            .completedAt(completedAt)
            .build();

        // When/Then
        assertEquals(completedAt, invoice.getCompletedAt());
    }

    @Test
    void testBuilderWithErrorMessage() {
        // Given
        String errorMessage = "Test error";
        ProcessedTaxInvoice invoice = validInvoiceBuilder
            .errorMessage(errorMessage)
            .build();

        // When/Then
        assertEquals(errorMessage, invoice.getErrorMessage());
    }

    @Test
    void testCompleteWorkflow() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When/Then - Complete workflow
        assertEquals(ProcessingStatus.PENDING, invoice.getStatus());

        invoice.startProcessing();
        assertEquals(ProcessingStatus.PROCESSING, invoice.getStatus());

        invoice.markCompleted();
        assertEquals(ProcessingStatus.COMPLETED, invoice.getStatus());
        assertNotNull(invoice.getCompletedAt());

        invoice.requestPdfGeneration();
        assertEquals(ProcessingStatus.PDF_REQUESTED, invoice.getStatus());

        invoice.markPdfGenerated();
        assertEquals(ProcessingStatus.PDF_GENERATED, invoice.getStatus());
    }

    @Test
    void testCachedTotals() {
        // Given
        ProcessedTaxInvoice invoice = validInvoiceBuilder.build();

        // When - Call multiple times
        Money subtotal1 = invoice.getSubtotal();
        Money subtotal2 = invoice.getSubtotal();
        Money tax1 = invoice.getTotalTax();
        Money tax2 = invoice.getTotalTax();
        Money total1 = invoice.getTotal();
        Money total2 = invoice.getTotal();

        // Then - Should return same instances (cached)
        assertSame(subtotal1, subtotal2);
        assertSame(tax1, tax2);
        assertSame(total1, total2);
    }

    @Test
    void testAllGetters() {
        // Given
        TaxInvoiceId id = TaxInvoiceId.generate();
        LocalDate issueDate = LocalDate.of(2025, 1, 1);
        LocalDate dueDate = LocalDate.of(2025, 2, 1);

        ProcessedTaxInvoice invoice = validInvoiceBuilder
            .id(id)
            .issueDate(issueDate)
            .dueDate(dueDate)
            .build();

        // When/Then
        assertEquals(id, invoice.getId());
        assertEquals("intake-123", invoice.getSourceInvoiceId());
        assertEquals("INV-001", invoice.getInvoiceNumber());
        assertEquals(issueDate, invoice.getIssueDate());
        assertEquals(dueDate, invoice.getDueDate());
        assertNotNull(invoice.getSeller());
        assertNotNull(invoice.getBuyer());
        assertEquals(1, invoice.getItems().size());
        assertEquals("THB", invoice.getCurrency());
        assertEquals("<xml>test</xml>", invoice.getOriginalXml());
        assertEquals(ProcessingStatus.PENDING, invoice.getStatus());
        assertNotNull(invoice.getCreatedAt());
        assertNull(invoice.getCompletedAt());
        assertNull(invoice.getErrorMessage());
    }
}
