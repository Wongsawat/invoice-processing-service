package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.CompensationLogEntry;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compensation_log", indexes = {
    @Index(name = "idx_compensation_source_invoice", columnList = "source_invoice_id"),
    @Index(name = "idx_compensation_saga",           columnList = "saga_id"),
    @Index(name = "idx_compensation_at",             columnList = "compensated_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompensationLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_invoice_id", nullable = false, length = 100)
    private String sourceInvoiceId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "saga_id", nullable = false, length = 255)
    private String sagaId;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "reason", nullable = false, length = 20)
    private String reason;

    @Column(name = "compensated_at", nullable = false)
    private Instant compensatedAt;

    public static CompensationLogEntity fromDomain(CompensationLogEntry entry) {
        return CompensationLogEntity.builder()
            .id(entry.id())
            .sourceInvoiceId(entry.sourceInvoiceId())
            .invoiceId(entry.invoiceId() != null ? entry.invoiceId().value() : null)
            .invoiceNumber(entry.invoiceNumber())
            .sagaId(entry.sagaId())
            .correlationId(entry.correlationId())
            .reason(entry.reason().name())
            .compensatedAt(entry.compensatedAt())
            .build();
    }
}
