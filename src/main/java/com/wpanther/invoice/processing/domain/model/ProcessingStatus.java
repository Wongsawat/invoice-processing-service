package com.wpanther.invoice.processing.domain.model;

/**
 * Enum representing the processing status of an invoice
 */
public enum ProcessingStatus {
    /**
     * Invoice has been received and is pending processing
     */
    PENDING,

    /**
     * Invoice is currently being processed
     */
    PROCESSING,

    /**
     * Invoice has been successfully processed
     */
    COMPLETED,

    /**
     * Invoice processing has failed
     */
    FAILED
}
