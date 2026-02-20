package com.wpanther.taxinvoice.processing.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JpaOutboxEventRepository
 */
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaOutboxEventRepository.class)
@ActiveProfiles("test")
class JpaOutboxEventRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SpringDataOutboxRepository springDataOutboxRepository;

    @Autowired
    private JpaOutboxEventRepository jpaOutboxEventRepository;

    @Test
    void testSaveOutboxEvent() {
        // Given
        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();

        // When
        OutboxEventEntity saved = springDataOutboxRepository.save(event);

        // Then
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("ProcessedTaxInvoice", saved.getAggregateType());
        assertEquals("taxinvoice.processed", saved.getEventType());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals("taxinvoice.processed", saved.getTopic());
        assertEquals("invoice-123", saved.getPartitionKey());
    }

    @Test
    void testFindById() {
        // Given
        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        entityManager.persist(event);
        entityManager.flush();

        // When
        Optional<OutboxEventEntity> found = springDataOutboxRepository.findById(event.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(event.getId(), found.get().getId());
        assertEquals("ProcessedTaxInvoice", found.get().getAggregateType());
    }

    @Test
    void testUpdateStatus() {
        // Given
        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        entityManager.persist(event);
        entityManager.flush();

        // When
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        springDataOutboxRepository.save(event);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<OutboxEventEntity> updated = springDataOutboxRepository.findById(event.getId());
        assertTrue(updated.isPresent());
        assertEquals(OutboxStatus.PUBLISHED, updated.get().getStatus());
        assertNotNull(updated.get().getPublishedAt());
    }

    @Test
    void testFindByStatusOrderByCreatedAtAsc() {
        // Given
        Instant baseTime = Instant.now();
        OutboxEventEntity event1 = createOutboxEvent(OutboxStatus.PENDING, baseTime.minusSeconds(100));
        OutboxEventEntity event2 = createOutboxEvent(OutboxStatus.PUBLISHED, baseTime.minusSeconds(90));
        OutboxEventEntity event3 = createOutboxEvent(OutboxStatus.PENDING, baseTime.minusSeconds(80));
        entityManager.persist(event1);
        entityManager.persist(event2);
        entityManager.persist(event3);
        entityManager.flush();

        // When
        List<OutboxEventEntity> pendingEvents = springDataOutboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 10)
        );

        // Then
        assertEquals(2, pendingEvents.size());
        assertEquals(event1.getId(), pendingEvents.get(0).getId());
        assertEquals(event3.getId(), pendingEvents.get(1).getId());
    }

    @Test
    void testDeleteById() {
        // Given
        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        entityManager.persist(event);
        entityManager.flush();

        // When
        springDataOutboxRepository.deleteById(event.getId());
        entityManager.flush();

        // Then
        Optional<OutboxEventEntity> deleted = springDataOutboxRepository.findById(event.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    void testFindByAggregateTypeAndAggregateIdOrderByCreatedAtAsc() {
        // Given
        String aggregateId = UUID.randomUUID().toString();
        String aggregateType = "ProcessedTaxInvoice";
        OutboxEventEntity event1 = OutboxEventEntity.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        OutboxEventEntity event2 = OutboxEventEntity.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType("xml.signing.requested")
                .payload("{\"test\": \"data2\"}")
                .status(OutboxStatus.PUBLISHED)
                .topic("xml.signing.requested")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        entityManager.persist(event1);
        entityManager.persist(event2);
        entityManager.flush();

        // When
        List<OutboxEventEntity> events = springDataOutboxRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                aggregateType, aggregateId
        );

        // Then
        assertEquals(2, events.size());
    }

    @Test
    void testDeletePublishedBefore() {
        // Given
        Instant baseTime = Instant.now();
        OutboxEventEntity oldEvent = OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PUBLISHED)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .publishedAt(baseTime.minusSeconds(3600))
                .createdAt(baseTime.minusSeconds(3700))
                .build();
        OutboxEventEntity recentEvent = OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data2\"}")
                .status(OutboxStatus.PUBLISHED)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-456")
                .headers("{\"correlationId\": \"test-456\"}")
                .publishedAt(baseTime.minusSeconds(100))
                .createdAt(baseTime.minusSeconds(200))
                .build();
        entityManager.persist(oldEvent);
        entityManager.persist(recentEvent);
        entityManager.flush();

        // When
        int deleted = springDataOutboxRepository.deletePublishedBefore(baseTime.minusSeconds(300));

        // Then
        assertEquals(1, deleted);
        assertFalse(springDataOutboxRepository.existsById(oldEvent.getId()));
        assertTrue(springDataOutboxRepository.existsById(recentEvent.getId()));
    }

    private OutboxEventEntity createOutboxEvent(OutboxStatus status, Instant createdAt) {
        return OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(status)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .createdAt(createdAt)
                .build();
    }

    // Tests for JpaOutboxEventRepository

    @Test
    void testJpaRepositorySave() {
        // Given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();

        // When
        OutboxEvent saved = jpaOutboxEventRepository.save(event);

        // Then
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("ProcessedTaxInvoice", saved.getAggregateType());
        assertEquals("taxinvoice.processed", saved.getEventType());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
    }

    @Test
    void testJpaRepositoryFindById() {
        // Given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        OutboxEvent saved = jpaOutboxEventRepository.save(event);

        // When
        Optional<OutboxEvent> found = jpaOutboxEventRepository.findById(saved.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals("ProcessedTaxInvoice", found.get().getAggregateType());
    }

    @Test
    void testJpaRepositoryFindPendingEvents() {
        // Given
        Instant baseTime = Instant.now();
        OutboxEvent pendingEvent1 = createDomainOutboxEvent(OutboxStatus.PENDING, baseTime.minusSeconds(100));
        OutboxEvent pendingEvent2 = createDomainOutboxEvent(OutboxStatus.PENDING, baseTime.minusSeconds(80));
        OutboxEvent publishedEvent = createDomainOutboxEvent(OutboxStatus.PUBLISHED, baseTime.minusSeconds(90));
        jpaOutboxEventRepository.save(pendingEvent1);
        jpaOutboxEventRepository.save(publishedEvent);
        jpaOutboxEventRepository.save(pendingEvent2);

        // When
        List<OutboxEvent> pendingEvents = jpaOutboxEventRepository.findPendingEvents(10);

        // Then
        assertEquals(2, pendingEvents.size());
    }

    @Test
    void testJpaRepositoryFindFailedEvents() {
        // Given
        OutboxEvent failedEvent = createDomainOutboxEvent(OutboxStatus.FAILED, Instant.now());
        OutboxEvent pendingEvent = createDomainOutboxEvent(OutboxStatus.PENDING, Instant.now());
        jpaOutboxEventRepository.save(failedEvent);
        jpaOutboxEventRepository.save(pendingEvent);

        // When
        List<OutboxEvent> failedEvents = jpaOutboxEventRepository.findFailedEvents(10);

        // Then
        assertEquals(1, failedEvents.size());
        assertEquals(OutboxStatus.FAILED, failedEvents.get(0).getStatus());
    }

    @Test
    void testJpaRepositoryDeletePublishedBefore() {
        // Given
        Instant baseTime = Instant.now();
        OutboxEvent oldEvent = OutboxEvent.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PUBLISHED)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .publishedAt(baseTime.minusSeconds(3600))
                .createdAt(baseTime.minusSeconds(3700))
                .build();
        OutboxEvent recentEvent = OutboxEvent.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data2\"}")
                .status(OutboxStatus.PUBLISHED)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-456")
                .headers("{\"correlationId\": \"test-456\"}")
                .publishedAt(baseTime.minusSeconds(100))
                .createdAt(baseTime.minusSeconds(200))
                .build();
        jpaOutboxEventRepository.save(oldEvent);
        jpaOutboxEventRepository.save(recentEvent);

        // When
        int deleted = jpaOutboxEventRepository.deletePublishedBefore(baseTime.minusSeconds(300));

        // Then
        assertEquals(1, deleted);
    }

    @Test
    void testJpaRepositoryFindByAggregate() {
        // Given
        String aggregateId = UUID.randomUUID().toString();
        String aggregateType = "ProcessedTaxInvoice";
        OutboxEvent event1 = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(OutboxStatus.PENDING)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        OutboxEvent event2 = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType("xml.signing.requested")
                .payload("{\"test\": \"data2\"}")
                .status(OutboxStatus.PUBLISHED)
                .topic("xml.signing.requested")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .build();
        jpaOutboxEventRepository.save(event1);
        jpaOutboxEventRepository.save(event2);

        // When
        List<OutboxEvent> events = jpaOutboxEventRepository.findByAggregate(aggregateType, aggregateId);

        // Then
        assertEquals(2, events.size());
    }

    private OutboxEvent createDomainOutboxEvent(OutboxStatus status, Instant createdAt) {
        return OutboxEvent.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("taxinvoice.processed")
                .payload("{\"test\": \"data\"}")
                .status(status)
                .topic("taxinvoice.processed")
                .partitionKey("invoice-123")
                .headers("{\"correlationId\": \"test-123\"}")
                .createdAt(createdAt)
                .build();
    }
}
