package com.wpanther.taxinvoice.processing.domain.model;

/**
 * Enum representing the processing status of a tax invoice
 */
public enum ProcessingStatus {
    /**
     * Tax invoice has been received and is pending processing
     */
    PENDING,

    /**
     * Tax invoice is currently being processed
     */
    PROCESSING,

    /**
     * Tax invoice has been successfully processed
     */
    COMPLETED,

    /**
     * Tax invoice processing has failed
     */
    FAILED,

    /**
     * PDF generation has been requested
     */
    PDF_REQUESTED,

    /**
     * PDF has been successfully generated
     */
    PDF_GENERATED
}
