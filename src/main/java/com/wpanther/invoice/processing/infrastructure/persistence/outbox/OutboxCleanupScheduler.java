package com.wpanther.invoice.processing.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job that deletes published outbox events older than the configured
 * retention window.
 *
 * <p>Without cleanup the {@code outbox_events} table grows without bound.
 * Each processed invoice writes at least two rows (saga reply + notification
 * event), so at high volume the table accumulates quickly and will eventually
 * degrade Debezium CDC poll latency and {@code findPendingEvents} query
 * performance.
 *
 * <p><b>Compliance note:</b> this table is a transient Debezium CDC relay, not
 * a compliance store.  Long-term audit retention is the responsibility of the
 * document-storage-service (MinIO/S3). A 7-day window is sufficient here —
 * once Debezium has read and published an outbox row to Kafka the row has no
 * further operational value.
 *
 * <p>Configurable properties (with production-safe defaults):
 * <ul>
 *   <li>{@code app.outbox.cleanup.retention-days} — how many days to keep
 *       published events (default: 7)</li>
 *   <li>{@code app.outbox.cleanup.cron} — when to run (default: 02:00 daily
 *       in the JVM timezone; pin {@code TZ=Asia/Bangkok} in the container
 *       manifest for predictable Thai-business-hours behaviour)</li>
 * </ul>
 *
 * <p>Failures increment {@code outbox.cleanup.failure} so alerting can detect
 * when the table is no longer being pruned before it grows unbounded.
 */
@Component
@Slf4j
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final Counter cleanupFailureCounter;

    @Value("${app.outbox.cleanup.retention-days:7}")
    private int retentionDays;

    @Value("${app.outbox.cleanup.cron:0 0 2 * * *}")
    private String cleanupCron;

    public OutboxCleanupScheduler(OutboxEventRepository outboxEventRepository,
                                  MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.cleanupFailureCounter = Counter.builder("outbox.cleanup.failure")
            .description("Number of times the outbox cleanup job failed; sustained non-zero means the table may grow unbounded")
            .register(meterRegistry);
    }

    @PostConstruct
    void logConfiguration() {
        if (retentionDays < 1) {
            throw new IllegalStateException(
                "app.outbox.cleanup.retention-days must be >= 1, got: " + retentionDays);
        }
        log.info("OutboxCleanupScheduler initialized: retention={} days, cron='{}'",
            retentionDays, cleanupCron);
    }

    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 2 * * *}")
    public void cleanPublishedEvents() {
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
            log.info("Outbox cleanup: deleted {} published events older than {} days", deleted, retentionDays);
        } catch (Exception e) {
            cleanupFailureCounter.increment();
            log.error("Outbox cleanup failed: {}", e.toString());
        }
    }
}
