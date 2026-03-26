package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for ProcessedInvoiceEntity
 */
@Repository
public interface JpaProcessedInvoiceRepository extends JpaRepository<ProcessedInvoiceEntity, UUID> {

    /**
     * Find by invoice number
     */
    Optional<ProcessedInvoiceEntity> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find by source invoice ID with parties and line items eagerly loaded (avoids N+1 queries)
     */
    @Query("SELECT i FROM ProcessedInvoiceEntity i " +
           "LEFT JOIN FETCH i.parties " +
           "LEFT JOIN FETCH i.lineItems " +
           "WHERE i.sourceInvoiceId = :sourceInvoiceId")
    Optional<ProcessedInvoiceEntity> findBySourceInvoiceIdWithDetails(@Param("sourceInvoiceId") String sourceInvoiceId);

    /**
     * Find by processing status
     */
    List<ProcessedInvoiceEntity> findByStatus(ProcessingStatus status);

    /**
     * Check if invoice number exists
     */
    boolean existsByInvoiceNumber(String invoiceNumber);

    /**
     * Update only the mutable state fields (status, errorMessage, processedAt, updatedAt).
     * Used by save() on the update path to avoid loading the full entity.
     *
     * <p>{@code i.updatedAt = CURRENT_TIMESTAMP} is set explicitly because this
     * {@code @Modifying} JPQL query bypasses Hibernate lifecycle callbacks, so the
     * {@code @UpdateTimestamp} annotation on the entity field does not fire here.
     *
     * <p>{@code i.version = i.version + 1} keeps the optimistic-locking column
     * consistent with JPA-managed saves. Without this, the version stays at its
     * post-INSERT value after every status transition, so a concurrent entity-level
     * save would pass the {@code WHERE version = ?} check on stale data.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProcessedInvoiceEntity i " +
           "SET i.status = :status, i.errorMessage = :errorMessage, i.processedAt = :processedAt, " +
           "    i.updatedAt = CURRENT_TIMESTAMP, i.version = i.version + 1 " +
           "WHERE i.id = :id")
    int updateStatusFields(@Param("id") UUID id,
                           @Param("status") ProcessingStatus status,
                           @Param("errorMessage") String errorMessage,
                           @Param("processedAt") LocalDateTime processedAt);

    /**
     * Find invoice with parties and line items eagerly loaded
     */
    @Query("SELECT i FROM ProcessedInvoiceEntity i " +
           "LEFT JOIN FETCH i.parties " +
           "LEFT JOIN FETCH i.lineItems " +
           "WHERE i.id = :id")
    Optional<ProcessedInvoiceEntity> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Find invoices by status with details
     */
    @Query("SELECT DISTINCT i FROM ProcessedInvoiceEntity i " +
           "LEFT JOIN FETCH i.parties " +
           "LEFT JOIN FETCH i.lineItems " +
           "WHERE i.status = :status")
    List<ProcessedInvoiceEntity> findByStatusWithDetails(@Param("status") ProcessingStatus status);
}
