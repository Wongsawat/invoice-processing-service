package com.wpanther.taxinvoice.processing.infrastructure.persistence;

import com.wpanther.taxinvoice.processing.domain.model.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for ProcessedTaxInvoiceEntity
 */
@Repository
public interface JpaProcessedTaxInvoiceRepository extends JpaRepository<ProcessedTaxInvoiceEntity, UUID> {

    /**
     * Find by invoice number
     */
    Optional<ProcessedTaxInvoiceEntity> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find by source invoice ID
     */
    Optional<ProcessedTaxInvoiceEntity> findBySourceInvoiceId(String sourceInvoiceId);

    /**
     * Find by processing status
     */
    List<ProcessedTaxInvoiceEntity> findByStatus(ProcessingStatus status);

    /**
     * Check if invoice number exists
     */
    boolean existsByInvoiceNumber(String invoiceNumber);

    /**
     * Find invoice with parties and line items eagerly loaded
     */
    @Query("SELECT i FROM ProcessedTaxInvoiceEntity i " +
           "LEFT JOIN FETCH i.parties " +
           "LEFT JOIN FETCH i.lineItems " +
           "WHERE i.id = :id")
    Optional<ProcessedTaxInvoiceEntity> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Find invoices by status with details
     */
    @Query("SELECT DISTINCT i FROM ProcessedTaxInvoiceEntity i " +
           "LEFT JOIN FETCH i.parties " +
           "LEFT JOIN FETCH i.lineItems " +
           "WHERE i.status = :status")
    List<ProcessedTaxInvoiceEntity> findByStatusWithDetails(@Param("status") ProcessingStatus status);
}
