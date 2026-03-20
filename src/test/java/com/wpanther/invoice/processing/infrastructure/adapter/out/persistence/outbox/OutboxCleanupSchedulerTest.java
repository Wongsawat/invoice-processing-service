package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxCleanupSchedulerTest {

    private OutboxEventRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private OutboxCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new OutboxCleanupScheduler(repository, meterRegistry);
    }

    @Test
    void cleanPublishedEvents_whenSuccessful_deletesEventsAndDoesNotIncrementFailureCounter() {
        when(repository.deletePublishedBefore(any(Instant.class))).thenReturn(42);

        scheduler.cleanPublishedEvents();

        verify(repository).deletePublishedBefore(any(Instant.class));
        assertEquals(0.0, failureCount(), "Failure counter must stay at zero on success");
    }

    @Test
    void cleanPublishedEvents_whenRepositoryThrows_incrementsFailureCounterAndDoesNotPropagate() {
        when(repository.deletePublishedBefore(any(Instant.class)))
            .thenThrow(new RuntimeException("DB connection lost"));

        // Must not propagate — scheduler must not crash the Spring scheduling thread
        scheduler.cleanPublishedEvents();

        assertEquals(1.0, failureCount(),
            "Failure counter must be incremented when cleanup throws");
    }

    @Test
    void cleanPublishedEvents_whenRepositoryThrowsRepeatedly_accumulatesFailureCount() {
        when(repository.deletePublishedBefore(any(Instant.class)))
            .thenThrow(new RuntimeException("DB connection lost"));

        scheduler.cleanPublishedEvents();
        scheduler.cleanPublishedEvents();
        scheduler.cleanPublishedEvents();

        assertEquals(3.0, failureCount(),
            "Failure counter must accumulate across repeated failures");
    }

    @Test
    void logConfiguration_whenRetentionDaysIsZero_throwsIllegalStateException() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 0);
        assertThrows(IllegalStateException.class, scheduler::logConfiguration,
            "retentionDays=0 must be rejected at startup");
    }

    @Test
    void logConfiguration_whenRetentionDaysIsNegative_throwsIllegalStateException() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", -1);
        assertThrows(IllegalStateException.class, scheduler::logConfiguration,
            "Negative retentionDays must be rejected at startup");
    }

    @Test
    void logConfiguration_whenRetentionDaysIsOne_doesNotThrow() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 1);
        scheduler.logConfiguration(); // must not throw
    }

    private double failureCount() {
        Counter counter = meterRegistry.find("outbox.cleanup.failure").counter();
        return counter == null ? 0.0 : counter.count();
    }
}
