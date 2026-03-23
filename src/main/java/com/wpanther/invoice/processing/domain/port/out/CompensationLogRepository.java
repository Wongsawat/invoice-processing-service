package com.wpanther.invoice.processing.domain.port.out;

import com.wpanther.invoice.processing.domain.model.CompensationLogEntry;

/**
 * Outbound port — persistence contract for CompensationLogEntry.
 * Domain dictates the contract; infrastructure provides the implementation.
 */
public interface CompensationLogRepository {

    /**
     * Persists a compensation log entry.
     * Must be called within an active transaction (Propagation.MANDATORY enforced by impl).
     */
    void save(CompensationLogEntry entry);
}
