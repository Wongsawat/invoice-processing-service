package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaOutboxEventRepositoryTest {

    @Mock
    private SpringDataOutboxRepository springRepository;

    private JpaOutboxEventRepository repository;

    private UUID testId;
    private OutboxEventEntity testEntity;
    private OutboxEvent testDomainEvent;

    @BeforeEach
    void setUp() {
        repository = new JpaOutboxEventRepository(springRepository);
        testId = UUID.randomUUID();

        testEntity = OutboxEventEntity.builder()
            .id(testId)
            .aggregateType("ProcessedInvoice")
            .aggregateId("agg-1")
            .eventType("InvoiceProcessedEvent")
            .payload("{\"invoiceId\":\"inv-1\"}")
            .createdAt(Instant.now())
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .topic("invoice.processed")
            .partitionKey("agg-1")
            .headers("{}")
            .build();

        testDomainEvent = testEntity.toDomain();
    }

    @Test
    void save_convertsToEntityAndReturnsAsDomain() {
        when(springRepository.save(any(OutboxEventEntity.class))).thenReturn(testEntity);

        OutboxEvent result = repository.save(testDomainEvent);

        verify(springRepository).save(argThat(entity ->
            entity.getId().equals(testId) &&
            entity.getAggregateType().equals("ProcessedInvoice")
        ));
        assertEquals(testId, result.getId());
        assertEquals("ProcessedInvoice", result.getAggregateType());
    }

    @Test
    void findById_whenFound_returnsDomainEvent() {
        when(springRepository.findById(testId)).thenReturn(Optional.of(testEntity));

        Optional<OutboxEvent> result = repository.findById(testId);

        assertTrue(result.isPresent());
        assertEquals(testId, result.get().getId());
    }

    @Test
    void findById_whenNotFound_returnsEmpty() {
        UUID unknownId = UUID.randomUUID();
        when(springRepository.findById(unknownId)).thenReturn(Optional.empty());

        Optional<OutboxEvent> result = repository.findById(unknownId);

        assertFalse(result.isPresent());
    }

    @Test
    void findPendingEvents_returnsPagedPendingEvents() {
        when(springRepository.findByStatusOrderByCreatedAtAsc(
            eq(OutboxStatus.PENDING), any(Pageable.class)))
            .thenReturn(List.of(testEntity));

        List<OutboxEvent> results = repository.findPendingEvents(10);

        assertEquals(1, results.size());
        assertEquals(testId, results.get(0).getId());
        assertEquals(OutboxStatus.PENDING, results.get(0).getStatus());
    }

    @Test
    void findPendingEvents_whenNone_returnsEmptyList() {
        when(springRepository.findByStatusOrderByCreatedAtAsc(
            eq(OutboxStatus.PENDING), any(Pageable.class)))
            .thenReturn(List.of());

        List<OutboxEvent> results = repository.findPendingEvents(10);

        assertTrue(results.isEmpty());
    }

    @Test
    void findFailedEvents_returnsPagedFailedEvents() {
        OutboxEventEntity failedEntity = OutboxEventEntity.builder()
            .id(UUID.randomUUID())
            .aggregateType("ProcessedInvoice")
            .aggregateId("agg-2")
            .eventType("InvoiceProcessedEvent")
            .payload("{}")
            .createdAt(Instant.now())
            .status(OutboxStatus.FAILED)
            .retryCount(2)
            .errorMessage("Kafka unavailable")
            .topic("invoice.processed")
            .build();

        when(springRepository.findByStatusOrderByCreatedAtAsc(
            eq(OutboxStatus.FAILED), any(Pageable.class)))
            .thenReturn(List.of(failedEntity));

        List<OutboxEvent> results = repository.findFailedEvents(5);

        assertEquals(1, results.size());
        assertEquals(OutboxStatus.FAILED, results.get(0).getStatus());
        assertEquals("Kafka unavailable", results.get(0).getErrorMessage());
    }

    @Test
    void deletePublishedBefore_delegatesToRepository() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        when(springRepository.deletePublishedBefore(cutoff)).thenReturn(42);

        int deleted = repository.deletePublishedBefore(cutoff);

        assertEquals(42, deleted);
        verify(springRepository).deletePublishedBefore(cutoff);
    }

    @Test
    void deletePublishedBefore_whenNoneFound_returnsZero() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        when(springRepository.deletePublishedBefore(cutoff)).thenReturn(0);

        int deleted = repository.deletePublishedBefore(cutoff);

        assertEquals(0, deleted);
    }

    @Test
    void findByAggregate_returnsEventsForAggregateTypeAndId() {
        OutboxEventEntity anotherEntity = OutboxEventEntity.builder()
            .id(UUID.randomUUID())
            .aggregateType("ProcessedInvoice")
            .aggregateId("agg-1")
            .eventType("InvoiceReplyEvent")
            .payload("{}")
            .createdAt(Instant.now())
            .status(OutboxStatus.PUBLISHED)
            .retryCount(0)
            .topic("saga.reply.invoice")
            .build();

        when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            "ProcessedInvoice", "agg-1"))
            .thenReturn(List.of(testEntity, anotherEntity));

        List<OutboxEvent> results = repository.findByAggregate("ProcessedInvoice", "agg-1");

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(e -> e.getAggregateType().equals("ProcessedInvoice")));
        assertTrue(results.stream().allMatch(e -> e.getAggregateId().equals("agg-1")));
    }

    @Test
    void findByAggregate_whenNoneFound_returnsEmptyList() {
        when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            "ProcessedInvoice", "unknown-id"))
            .thenReturn(List.of());

        List<OutboxEvent> results = repository.findByAggregate("ProcessedInvoice", "unknown-id");

        assertTrue(results.isEmpty());
    }
}
