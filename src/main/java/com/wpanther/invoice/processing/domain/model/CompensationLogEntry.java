package com.wpanther.invoice.processing.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a single saga compensation event.
 * Written atomically with the corresponding ProcessedInvoice deletion.
 * Never deleted — serves as permanent audit trail.
 */
public record CompensationLogEntry(
        UUID id,
        String sourceInvoiceId,
        InvoiceId invoiceId,        // null when ALREADY_ABSENT
        String invoiceNumber,        // null when ALREADY_ABSENT
        String sagaId,
        String correlationId,
        CompensationReason reason,
        Instant compensatedAt
) {

    public enum CompensationReason {
        /** Invoice row was found and deleted during this compensation. */
        COMPENSATED,
        /** Invoice row was not found — already deleted or never processed. */
        ALREADY_ABSENT
    }

    /**
     * Factory: invoice was found and is being deleted now.
     */
    public static CompensationLogEntry compensated(
            String sourceInvoiceId, InvoiceId invoiceId,
            String invoiceNumber, String sagaId, String correlationId) {
        return new CompensationLogEntry(
            UUID.randomUUID(),
            Objects.requireNonNull(sourceInvoiceId),
            Objects.requireNonNull(invoiceId),
            invoiceNumber,
            sagaId,
            correlationId,
            CompensationReason.COMPENSATED,
            Instant.now()
        );
    }

    /**
     * Factory: invoice was not found (already deleted or never processed).
     */
    public static CompensationLogEntry alreadyAbsent(
            String sourceInvoiceId, String sagaId, String correlationId) {
        return new CompensationLogEntry(
            UUID.randomUUID(),
            Objects.requireNonNull(sourceInvoiceId),
            null,
            null,
            sagaId,
            correlationId,
            CompensationReason.ALREADY_ABSENT,
            Instant.now()
        );
    }
}
