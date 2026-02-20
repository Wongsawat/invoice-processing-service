package com.wpanther.invoice.processing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessingStatus enum
 */
class ProcessingStatusTest {

    @Test
    void testAllStatusValues() {
        // When
        ProcessingStatus[] statuses = ProcessingStatus.values();

        // Then
        assertEquals(6, statuses.length);
        assertArrayEquals(
            new ProcessingStatus[]{
                ProcessingStatus.PENDING,
                ProcessingStatus.PROCESSING,
                ProcessingStatus.COMPLETED,
                ProcessingStatus.FAILED,
                ProcessingStatus.PDF_REQUESTED,
                ProcessingStatus.PDF_GENERATED
            },
            statuses
        );
    }

    @Test
    void testValueOf() {
        // When/Then
        assertEquals(ProcessingStatus.PENDING, ProcessingStatus.valueOf("PENDING"));
        assertEquals(ProcessingStatus.PROCESSING, ProcessingStatus.valueOf("PROCESSING"));
        assertEquals(ProcessingStatus.COMPLETED, ProcessingStatus.valueOf("COMPLETED"));
        assertEquals(ProcessingStatus.PDF_REQUESTED, ProcessingStatus.valueOf("PDF_REQUESTED"));
        assertEquals(ProcessingStatus.PDF_GENERATED, ProcessingStatus.valueOf("PDF_GENERATED"));
    }

    @Test
    void testValueOfInvalid() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            ProcessingStatus.valueOf("INVALID_STATUS")
        );
    }

    @Test
    void testEnumEquality() {
        // When/Then
        assertSame(ProcessingStatus.PENDING, ProcessingStatus.valueOf("PENDING"));
        assertSame(ProcessingStatus.COMPLETED, ProcessingStatus.valueOf("COMPLETED"));
    }
}
