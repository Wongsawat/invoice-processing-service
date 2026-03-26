package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessedInvoiceRepositoryImpl — update path (non-PROCESSING status).
 *
 * <p>These tests verify two properties of the update path:
 * <ol>
 *   <li>{@code existsById} is never called — row count from {@code updateStatusFields}
 *       is the sole indicator of whether the row exists.</li>
 *   <li>When {@code updateStatusFields} returns {@code 0} (no rows matched), an
 *       {@link IllegalStateException} is thrown — contract violation, not a silent no-op.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProcessedInvoiceRepositoryImplSaveUpdateTest {

    @Mock
    private JpaProcessedInvoiceRepository jpaRepository;

    @Mock
    private ProcessedInvoiceMapper mapper;

    @InjectMocks
    private ProcessedInvoiceRepositoryImpl repository;

    private ProcessedInvoice completedInvoice;

    @BeforeEach
    void setUp() {
        Party seller = Party.of(
            "Seller", TaxIdentifier.of("1234567890", "VAT"),
            new Address("Street", "Bangkok", "10110", "TH"), null
        );
        Party buyer = Party.of(
            "Buyer", TaxIdentifier.of("9876543210", "VAT"),
            new Address("Road", "Chiang Mai", "50000", "TH"), null
        );
        LineItem item = new LineItem("Service", 1, Money.of(100.00, "THB"), BigDecimal.ZERO);

        completedInvoice = ProcessedInvoice.builder()
            .id(InvoiceId.generate())
            .sourceInvoiceId("src-001")
            .invoiceNumber("INV-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .dueDate(LocalDate.of(2025, 2, 1))
            .seller(seller)
            .buyer(buyer)
            .addItem(item)
            .currency("THB")
            .originalXml("<xml/>")
            .build();
        completedInvoice.startProcessing();
        completedInvoice.markCompleted();  // status = COMPLETED → takes the update path
    }

    /**
     * Update path must not issue a separate SELECT to check existence.
     * The row count returned by {@code updateStatusFields} is the sole indicator.
     */
    @Test
    void save_onUpdatePath_doesNotCallExistsById() {
        // Given — row found; update succeeds
        when(jpaRepository.updateStatusFields(any(), any(), any(), any())).thenReturn(1);

        // When
        repository.save(completedInvoice);

        // Then — existsById must NOT be called; row-count replaces the SELECT guard
        verify(jpaRepository, never()).existsById(any());
    }

    /**
     * When {@code updateStatusFields} returns 0 (invoice was never INSERTed), the
     * repository must throw {@link IllegalStateException} rather than silently losing
     * the state transition.
     */
    @Test
    void save_onUpdatePath_whenZeroRowsUpdated_throwsIllegalStateException() {
        // lenient: the current (pre-fix) impl calls existsById before updateStatusFields;
        // this stub makes the old guard pass so we can observe that the impl fails to
        // throw when updateStatusFields returns 0.  After the fix, existsById is never
        // called, so this stub becomes irrelevant (lenient prevents a strict-stub warning).
        lenient().when(jpaRepository.existsById(any())).thenReturn(true);

        // Given — no row matched (invoice was never INSERTed — contract violation)
        when(jpaRepository.updateStatusFields(any(), any(), any(), any())).thenReturn(0);

        // When / Then — must throw; the current impl silently ignores the 0 return value
        assertThrows(IllegalStateException.class, () -> repository.save(completedInvoice));
    }
}
