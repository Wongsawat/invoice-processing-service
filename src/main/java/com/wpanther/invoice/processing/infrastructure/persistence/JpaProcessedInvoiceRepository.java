package com.wpanther.invoice.processing.infrastructure.persistence;

import com.wpanther.invoice.processing.domain.model.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Find by source invoice ID
     */
    Optional<ProcessedInvoiceEntity> findBySourceInvoiceId(String sourceInvoiceId);

    /**
     * Find by processing status
     */
    List<ProcessedInvoiceEntity> findByStatus(ProcessingStatus status);

    /**
     * Check if invoice number exists
     */
    boolean existsByInvoiceNumber(String invoiceNumber);

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
