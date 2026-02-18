package com.wpanther.invoice.processing.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of OutboxEventRepository from saga-commons.
 * Bridges between Spring Data repository and domain repository interface.
 */
@Component
@RequiredArgsConstructor
public class JpaOutboxEventRepository implements OutboxEventRepository {

    private final SpringDataOutboxRepository springRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventEntity entity = OutboxEventEntity.fromDomain(event);
        OutboxEventEntity saved = springRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return springRepository.findById(eventId)
                .map(OutboxEventEntity::toDomain);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        List<OutboxEventEntity> entities = springRepository.findByStatus(OutboxStatus.PENDING);
        return entities.stream()
                .limit(limit)
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public List<OutboxEvent> findFailedEvents(int limit) {
        List<OutboxEventEntity> entities = springRepository.findByStatus(OutboxStatus.FAILED);
        return entities.stream()
                .limit(limit)
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId) {
        List<OutboxEventEntity> entities = springRepository.findByAggregateTypeAndAggregateId(aggregateType, aggregateId);
        return entities.stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public int deletePublishedBefore(Instant before) {
        List<OutboxEventEntity> entities = springRepository.findByStatus(OutboxStatus.PUBLISHED);
        List<OutboxEventEntity> toDelete = entities.stream()
                .filter(e -> e.getPublishedAt() != null && e.getPublishedAt().isBefore(before))
                .toList();
        springRepository.deleteAll(toDelete);
        return toDelete.size();
    }
}
