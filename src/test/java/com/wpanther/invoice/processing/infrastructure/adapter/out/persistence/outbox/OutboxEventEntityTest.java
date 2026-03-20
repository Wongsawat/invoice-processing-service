package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventEntityTest {

    @Test
    void fromDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant publishedAt = Instant.now();

        OutboxEvent domain = OutboxEvent.builder()
            .id(id)
            .aggregateType("ProcessedInvoice")
            .aggregateId("agg-123")
            .eventType("InvoiceProcessedEvent")
            .payload("{\"key\":\"value\"}")
            .createdAt(createdAt)
            .publishedAt(publishedAt)
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .errorMessage(null)
            .topic("invoice.processed")
            .partitionKey("partition-key-1")
            .headers("{\"correlationId\":\"corr-1\"}")
            .build();

        OutboxEventEntity entity = OutboxEventEntity.fromDomain(domain);

        assertEquals(id, entity.getId());
        assertEquals("ProcessedInvoice", entity.getAggregateType());
        assertEquals("agg-123", entity.getAggregateId());
        assertEquals("InvoiceProcessedEvent", entity.getEventType());
        assertEquals("{\"key\":\"value\"}", entity.getPayload());
        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(publishedAt, entity.getPublishedAt());
        assertEquals(OutboxStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getRetryCount());
        assertNull(entity.getErrorMessage());
        assertEquals("invoice.processed", entity.getTopic());
        assertEquals("partition-key-1", entity.getPartitionKey());
        assertEquals("{\"correlationId\":\"corr-1\"}", entity.getHeaders());
    }

    @Test
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        OutboxEventEntity entity = OutboxEventEntity.builder()
            .id(id)
            .aggregateType("ProcessedInvoice")
            .aggregateId("agg-456")
            .eventType("InvoiceReplyEvent")
            .payload("{\"sagaId\":\"saga-1\"}")
            .createdAt(createdAt)
            .publishedAt(null)
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .errorMessage(null)
            .topic("saga.reply.invoice")
            .partitionKey("saga-1")
            .headers("{\"status\":\"SUCCESS\"}")
            .build();

        OutboxEvent domain = entity.toDomain();

        assertEquals(id, domain.getId());
        assertEquals("ProcessedInvoice", domain.getAggregateType());
        assertEquals("agg-456", domain.getAggregateId());
        assertEquals("InvoiceReplyEvent", domain.getEventType());
        assertEquals("{\"sagaId\":\"saga-1\"}", domain.getPayload());
        assertEquals(createdAt, domain.getCreatedAt());
        assertNull(domain.getPublishedAt());
        assertEquals(OutboxStatus.PENDING, domain.getStatus());
        assertEquals(0, domain.getRetryCount());
        assertNull(domain.getErrorMessage());
        assertEquals("saga.reply.invoice", domain.getTopic());
        assertEquals("saga-1", domain.getPartitionKey());
        assertEquals("{\"status\":\"SUCCESS\"}", domain.getHeaders());
    }

    @Test
    void fromDomain_thenToDomain_roundTrip() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        OutboxEvent original = OutboxEvent.builder()
            .id(id)
            .aggregateType("ProcessedInvoice")
            .aggregateId("agg-789")
            .eventType("InvoiceProcessedEvent")
            .payload("{}")
            .createdAt(createdAt)
            .publishedAt(null)
            .status(OutboxStatus.FAILED)
            .retryCount(2)
            .errorMessage("Connection refused")
            .topic("invoice.processed")
            .partitionKey("key-1")
            .headers("{}")
            .build();

        OutboxEvent result = OutboxEventEntity.fromDomain(original).toDomain();

        assertEquals(original.getId(), result.getId());
        assertEquals(original.getAggregateType(), result.getAggregateType());
        assertEquals(original.getAggregateId(), result.getAggregateId());
        assertEquals(original.getEventType(), result.getEventType());
        assertEquals(original.getPayload(), result.getPayload());
        assertEquals(original.getCreatedAt(), result.getCreatedAt());
        assertEquals(original.getStatus(), result.getStatus());
        assertEquals(original.getRetryCount(), result.getRetryCount());
        assertEquals(original.getErrorMessage(), result.getErrorMessage());
        assertEquals(original.getTopic(), result.getTopic());
        assertEquals(original.getPartitionKey(), result.getPartitionKey());
        assertEquals(original.getHeaders(), result.getHeaders());
    }

    @Test
    void onCreate_setsDefaultsForNullFields() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateType("ProcessedInvoice");
        entity.setAggregateId("agg-1");
        entity.setEventType("InvoiceProcessedEvent");
        entity.setPayload("{}");

        // Simulate @PrePersist
        entity.onCreate();

        assertEquals(OutboxStatus.PENDING, entity.getStatus());
        assertNotNull(entity.getCreatedAt());
        assertEquals(0, entity.getRetryCount());
    }

    @Test
    void onCreate_doesNotOverrideExistingValues() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setStatus(OutboxStatus.FAILED);
        entity.setRetryCount(3);
        Instant existingCreatedAt = Instant.now().minusSeconds(60);
        entity.setCreatedAt(existingCreatedAt);

        entity.onCreate();

        assertEquals(OutboxStatus.FAILED, entity.getStatus());
        assertEquals(3, entity.getRetryCount());
        assertEquals(existingCreatedAt, entity.getCreatedAt());
    }

    @Test
    void builder_createsEntityWithAllFields() {
        UUID id = UUID.randomUUID();

        OutboxEventEntity entity = OutboxEventEntity.builder()
            .id(id)
            .aggregateType("ProcessedInvoice")
            .aggregateId("agg-1")
            .eventType("InvoiceProcessedEvent")
            .payload("{}")
            .createdAt(Instant.now())
            .status(OutboxStatus.PUBLISHED)
            .retryCount(1)
            .topic("invoice.processed")
            .build();

        assertEquals(id, entity.getId());
        assertEquals("ProcessedInvoice", entity.getAggregateType());
        assertEquals(OutboxStatus.PUBLISHED, entity.getStatus());
        assertEquals(1, entity.getRetryCount());
    }
}
