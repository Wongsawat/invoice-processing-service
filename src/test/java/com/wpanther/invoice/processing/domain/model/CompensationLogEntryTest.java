package com.wpanther.invoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CompensationLogEntryTest {

    @Test
    void factory_compensated_setsAllFields() {
        InvoiceId invoiceId = InvoiceId.generate();
        Instant before = Instant.now();

        CompensationLogEntry entry = CompensationLogEntry.compensated(
            "doc-123", invoiceId, "INV-001", "saga-1", "corr-1");
        Instant after = Instant.now();

        assertThat(entry.id()).isNotNull();
        assertThat(entry.sourceInvoiceId()).isEqualTo("doc-123");
        assertThat(entry.invoiceId()).isEqualTo(invoiceId);
        assertThat(entry.invoiceNumber()).isEqualTo("INV-001");
        assertThat(entry.sagaId()).isEqualTo("saga-1");
        assertThat(entry.correlationId()).isEqualTo("corr-1");
        assertThat(entry.reason()).isEqualTo(CompensationLogEntry.CompensationReason.COMPENSATED);
        assertThat(entry.compensatedAt()).isBetween(before, after);
    }

    @Test
    void factory_alreadyAbsent_setsNullInvoiceFields() {
        CompensationLogEntry entry = CompensationLogEntry.alreadyAbsent(
            "doc-456", "saga-2", "corr-2");

        assertThat(entry.sourceInvoiceId()).isEqualTo("doc-456");
        assertThat(entry.invoiceId()).isNull();
        assertThat(entry.invoiceNumber()).isNull();
        assertThat(entry.reason()).isEqualTo(CompensationLogEntry.CompensationReason.ALREADY_ABSENT);
    }

    @Test
    void compensated_requiresSourceInvoiceId() {
        assertThatThrownBy(() -> CompensationLogEntry.compensated(
            null, InvoiceId.generate(), "INV-001", "saga-1", "corr-1"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compensated_requiresInvoiceId() {
        assertThatThrownBy(() -> CompensationLogEntry.compensated(
            "doc-1", null, "INV-001", "saga-1", "corr-1"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void alreadyAbsent_requiresSourceInvoiceId() {
        assertThatThrownBy(() -> CompensationLogEntry.alreadyAbsent(null, "saga-1", "corr-1"))
            .isInstanceOf(NullPointerException.class);
    }
}
