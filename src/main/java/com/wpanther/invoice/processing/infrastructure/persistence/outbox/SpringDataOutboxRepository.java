package com.wpanther.invoice.processing.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for OutboxEventEntity.
 */
@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Find pending events that haven't been published yet.
     */
    List<OutboxEventEntity> findByStatus(OutboxStatus status);

    /**
     * Find events for a specific aggregate.
     */
    List<OutboxEventEntity> findByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);

    /**
     * Find events by aggregate type and aggregate ID (alternative method).
     */
    List<OutboxEventEntity> findByAggregateIdAndAggregateType(String aggregateId, String aggregateType);
}
