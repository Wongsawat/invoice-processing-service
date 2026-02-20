package com.wpanther.invoice.processing.infrastructure.persistence;

import com.wpanther.invoice.processing.domain.model.*;
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ProcessedInvoiceRepositoryImpl
 */
@DataJpaTest
@Import({ProcessedInvoiceRepositoryImpl.class, ProcessedInvoiceMapper.class})
@ActiveProfiles("test")
class ProcessedInvoiceRepositoryImplTest {

    @Autowired
    private ProcessedInvoiceRepository repository;

    private ProcessedInvoice testInvoice;

    @BeforeEach
    void setUp() {
        Party seller = Party.of(
            "Test Seller",
            TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 Street", "Bangkok", "10110", "TH"),
            "seller@test.com"
        );

        Party buyer = Party.of(
            "Test Buyer",
            TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Road", "Chiang Mai", "50000", "TH"),
            "buyer@test.com"
        );

        LineItem item = new LineItem(
            "Test Service",
            10,
            Money.of(1000.00, "THB"),
            new BigDecimal("7.00")
        );

        testInvoice = ProcessedInvoice.builder()
            .id(InvoiceId.generate())
            .sourceInvoiceId("intake-test-123")
            .invoiceNumber("INV-TEST-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(seller)
            .buyer(buyer)
            .addItem(item)
            .currency("THB")
            .originalXml("<xml>test</xml>")
            .build();
    }

    @Test
    void testSaveAndFindById() {
        // When
        ProcessedInvoice saved = repository.save(testInvoice);
        Optional<ProcessedInvoice> found = repository.findById(saved.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals(saved.getInvoiceNumber(), found.get().getInvoiceNumber());
        assertEquals(saved.getSourceInvoiceId(), found.get().getSourceInvoiceId());
    }

    @Test
    void testFindByIdNotFound() {
        // Given
        InvoiceId nonExistentId = InvoiceId.generate();

        // When
        Optional<ProcessedInvoice> found = repository.findById(nonExistentId);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    void testFindBySourceInvoiceId() {
        // Given
        repository.save(testInvoice);

        // When
        Optional<ProcessedInvoice> found = repository.findBySourceInvoiceId("intake-test-123");

        // Then
        assertTrue(found.isPresent());
        assertEquals("intake-test-123", found.get().getSourceInvoiceId());
        assertEquals("INV-TEST-001", found.get().getInvoiceNumber());
    }

    @Test
    void testFindBySourceInvoiceIdNotFound() {
        // When
        Optional<ProcessedInvoice> found = repository.findBySourceInvoiceId("non-existent");

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByStatus() {
        // Given
        repository.save(testInvoice);

        // When
        List<ProcessedInvoice> found = repository.findByStatus(ProcessingStatus.PENDING);

        // Then
        assertFalse(found.isEmpty());
        assertTrue(found.stream().anyMatch(i -> i.getId().equals(testInvoice.getId())));
    }

    @Test
    void testFindByStatusEmpty() {
        // When
        List<ProcessedInvoice> found = repository.findByStatus(ProcessingStatus.COMPLETED);

        // Then
        assertTrue(found.isEmpty());
    }

    @Test
    void testSavePreservesAllFields() {
        // When
        ProcessedInvoice saved = repository.save(testInvoice);
        Optional<ProcessedInvoice> found = repository.findById(saved.getId());

        // Then
        assertTrue(found.isPresent());
        ProcessedInvoice invoice = found.get();

        // Check all fields
        assertEquals(testInvoice.getInvoiceNumber(), invoice.getInvoiceNumber());
        assertEquals(testInvoice.getIssueDate(), invoice.getIssueDate());
        assertEquals(testInvoice.getDueDate(), invoice.getDueDate());
        assertEquals(testInvoice.getCurrency(), invoice.getCurrency());
        assertEquals(testInvoice.getStatus(), invoice.getStatus());
        assertEquals(testInvoice.getOriginalXml(), invoice.getOriginalXml());

        // Check calculated totals
        assertEquals(testInvoice.getSubtotal(), invoice.getSubtotal());
        assertEquals(testInvoice.getTotalTax(), invoice.getTotalTax());
        assertEquals(testInvoice.getTotal(), invoice.getTotal());

        // Check parties
        assertNotNull(invoice.getSeller());
        assertEquals(testInvoice.getSeller().name(), invoice.getSeller().name());
        assertNotNull(invoice.getBuyer());
        assertEquals(testInvoice.getBuyer().name(), invoice.getBuyer().name());

        // Check line items
        assertEquals(testInvoice.getItems().size(), invoice.getItems().size());
    }

    @Test
    void testUpdateInvoice() {
        // Given
        ProcessedInvoice saved = repository.save(testInvoice);
        saved.startProcessing();

        // When
        ProcessedInvoice updated = repository.save(saved);
        Optional<ProcessedInvoice> found = repository.findById(updated.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(ProcessingStatus.PROCESSING, found.get().getStatus());
    }

    @Test
    void testSaveMultipleInvoices() {
        // Given
        ProcessedInvoice invoice2 = ProcessedInvoice.builder()
            .id(InvoiceId.generate())
            .sourceInvoiceId("intake-test-456")
            .invoiceNumber("INV-TEST-002")
            .issueDate(LocalDate.of(2025, 1, 2))
            .dueDate(LocalDate.of(2025, 2, 2))
            .seller(testInvoice.getSeller())
            .buyer(testInvoice.getBuyer())
            .items(testInvoice.getItems())
            .currency("THB")
            .originalXml("<xml>test2</xml>")
            .build();

        // When
        repository.save(testInvoice);
        repository.save(invoice2);

        // Then
        Optional<ProcessedInvoice> found1 = repository.findBySourceInvoiceId("intake-test-123");
        Optional<ProcessedInvoice> found2 = repository.findBySourceInvoiceId("intake-test-456");

        assertTrue(found1.isPresent());
        assertTrue(found2.isPresent());
        assertNotEquals(found1.get().getId(), found2.get().getId());
    }

    @Test
    void testFindByStatusMultipleInvoices() {
        // Given
        ProcessedInvoice invoice2 = ProcessedInvoice.builder()
            .id(InvoiceId.generate())
            .sourceInvoiceId("intake-test-456")
            .invoiceNumber("INV-TEST-002")
            .issueDate(LocalDate.of(2025, 1, 2))
            .dueDate(LocalDate.of(2025, 2, 2))
            .seller(testInvoice.getSeller())
            .buyer(testInvoice.getBuyer())
            .items(testInvoice.getItems())
            .currency("THB")
            .originalXml("<xml>test2</xml>")
            .build();

        repository.save(testInvoice);
        repository.save(invoice2);

        // When
        List<ProcessedInvoice> found = repository.findByStatus(ProcessingStatus.PENDING);

        // Then
        assertEquals(2, found.size());
    }

    @Test
    void testCompleteWorkflowWithRepository() {
        // Given
        ProcessedInvoice saved = repository.save(testInvoice);

        // When - Simulate complete workflow
        saved.startProcessing();
        repository.save(saved);

        saved.markCompleted();
        repository.save(saved);

        saved.requestPdfGeneration();
        repository.save(saved);

        saved.markPdfGenerated();
        ProcessedInvoice finalInvoice = repository.save(saved);

        // Then
        Optional<ProcessedInvoice> found = repository.findById(finalInvoice.getId());
        assertTrue(found.isPresent());
        assertEquals(ProcessingStatus.PDF_GENERATED, found.get().getStatus());
        assertNotNull(found.get().getCompletedAt());
    }

    @Test
    void testSaveWithFailedStatus() {
        // Given
        testInvoice.markFailed("Test error message");

        // When
        ProcessedInvoice saved = repository.save(testInvoice);
        Optional<ProcessedInvoice> found = repository.findById(saved.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(ProcessingStatus.FAILED, found.get().getStatus());
        assertEquals("Test error message", found.get().getErrorMessage());
        assertNotNull(found.get().getCompletedAt());
    }
}
