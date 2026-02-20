package com.wpanther.taxinvoice.processing.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxEventEntity Tests")
class OutboxEventEntityTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build entity with all fields")
        void testBuilderWithAllFields() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // Act
            OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId("tax-inv-123")
                .eventType("TaxInvoiceProcessedEvent")
                .payload("{\"key\":\"value\"}")
                .createdAt(now)
                .publishedAt(now.plusSeconds(5))
                .status(OutboxStatus.PUBLISHED)
                .retryCount(0)
                .errorMessage(null)
                .topic("taxinvoice.processed")
                .partitionKey("tax-inv-123")
                .headers("{\"correlationId\":\"corr-123\"}")
                .build();

            // Assert
            assertNotNull(entity);
            assertEquals(id, entity.getId());
            assertEquals("ProcessedTaxInvoice", entity.getAggregateType());
            assertEquals("tax-inv-123", entity.getAggregateId());
            assertEquals("TaxInvoiceProcessedEvent", entity.getEventType());
            assertEquals("{\"key\":\"value\"}", entity.getPayload());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(now.plusSeconds(5), entity.getPublishedAt());
            assertEquals(OutboxStatus.PUBLISHED, entity.getStatus());
            assertEquals(0, entity.getRetryCount());
            assertNull(entity.getErrorMessage());
            assertEquals("taxinvoice.processed", entity.getTopic());
            assertEquals("tax-inv-123", entity.getPartitionKey());
            assertEquals("{\"correlationId\":\"corr-123\"}", entity.getHeaders());
        }

        @Test
        @DisplayName("Should build entity with minimal fields")
        void testBuilderWithMinimalFields() {
            // Act
            OutboxEventEntity entity = OutboxEventEntity.builder()
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId("tax-inv-456")
                .eventType("XmlSigningRequestedEvent")
                .payload("{}")
                .build();

            // Assert
            assertNotNull(entity);
            assertEquals("ProcessedTaxInvoice", entity.getAggregateType());
            assertEquals("tax-inv-456", entity.getAggregateId());
            assertEquals("XmlSigningRequestedEvent", entity.getEventType());
            assertEquals("{}", entity.getPayload());
        }
    }

    @Nested
    @DisplayName("@PrePersist Tests")
    class PrePersistTests {

        @Test
        @DisplayName("Should generate default values on create")
        void testPrePersistDefaults() {
            // Arrange
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setAggregateType("ProcessedTaxInvoice");
            entity.setAggregateId("tax-inv-789");
            entity.setEventType("TaxInvoiceProcessedEvent");
            entity.setPayload("{\"test\":true}");

            assertNull(entity.getId(), "ID should be null before onCreate");
            assertNull(entity.getStatus(), "Status should be null before onCreate");
            assertNull(entity.getCreatedAt(), "CreatedAt should be null before onCreate");
            assertNull(entity.getRetryCount(), "RetryCount should be null before onCreate");

            // Act
            entity.onCreate();

            // Assert
            assertNotNull(entity.getId(), "ID should be generated on create");
            assertEquals(OutboxStatus.PENDING, entity.getStatus(), "Status should default to PENDING");
            assertNotNull(entity.getCreatedAt(), "CreatedAt should be set on create");
            assertEquals(0, entity.getRetryCount(), "RetryCount should default to 0");
        }

        @Test
        @DisplayName("Should not override existing values in onCreate")
        void testPrePersistDoesNotOverride() {
            // Arrange
            UUID existingId = UUID.randomUUID();
            Instant existingCreatedAt = Instant.now().minusSeconds(60);
            OutboxStatus existingStatus = OutboxStatus.PUBLISHED;
            Integer existingRetryCount = 3;

            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setId(existingId);
            entity.setAggregateType("ProcessedTaxInvoice");
            entity.setAggregateId("tax-inv-999");
            entity.setEventType("TaxInvoiceProcessedEvent");
            entity.setPayload("{\"test\":true}");
            entity.setCreatedAt(existingCreatedAt);
            entity.setStatus(existingStatus);
            entity.setRetryCount(existingRetryCount);

            // Act
            entity.onCreate();

            // Assert
            assertEquals(existingId, entity.getId(), "Existing ID should not be overridden");
            assertEquals(existingCreatedAt, entity.getCreatedAt(), "Existing createdAt should not be overridden");
            assertEquals(existingStatus, entity.getStatus(), "Existing status should not be overridden");
            assertEquals(existingRetryCount, entity.getRetryCount(), "Existing retryCount should not be overridden");
        }
    }

    @Nested
    @DisplayName("Domain Conversion Tests")
    class DomainConversionTests {

        @Test
        @DisplayName("Should convert from domain OutboxEvent")
        void testFromDomain() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEvent domainEvent = OutboxEvent.builder()
                .id(id)
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId("tax-inv-from-domain")
                .eventType("TaxInvoiceProcessedEvent")
                .payload("{\"processed\":true}")
                .createdAt(now)
                .publishedAt(now.plusSeconds(10))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .errorMessage(null)
                .topic("taxinvoice.processed")
                .partitionKey("tax-inv-from-domain")
                .headers("{\"traceId\":\"trace-456\"}")
                .build();

            // Act
            OutboxEventEntity entity = OutboxEventEntity.fromDomain(domainEvent);

            // Assert
            assertNotNull(entity);
            assertEquals(id, entity.getId());
            assertEquals("ProcessedTaxInvoice", entity.getAggregateType());
            assertEquals("tax-inv-from-domain", entity.getAggregateId());
            assertEquals("TaxInvoiceProcessedEvent", entity.getEventType());
            assertEquals("{\"processed\":true}", entity.getPayload());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(now.plusSeconds(10), entity.getPublishedAt());
            assertEquals(OutboxStatus.PENDING, entity.getStatus());
            assertEquals(0, entity.getRetryCount());
            assertNull(entity.getErrorMessage());
            assertEquals("taxinvoice.processed", entity.getTopic());
            assertEquals("tax-inv-from-domain", entity.getPartitionKey());
            assertEquals("{\"traceId\":\"trace-456\"}", entity.getHeaders());
        }

        @Test
        @DisplayName("Should convert to domain OutboxEvent")
        void testToDomain() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId("tax-inv-to-domain")
                .eventType("XmlSigningRequestedEvent")
                .payload("{\"signing\":true}")
                .createdAt(now)
                .status(OutboxStatus.PUBLISHED)
                .retryCount(1)
                .errorMessage("Temporary error")
                .topic("xml.signing.requested")
                .partitionKey("tax-inv-to-domain")
                .headers("{\"key\":\"value\"}")
                .build();

            // Act
            OutboxEvent domainEvent = entity.toDomain();

            // Assert
            assertNotNull(domainEvent);
            assertEquals(id, domainEvent.getId());
            assertEquals("ProcessedTaxInvoice", domainEvent.getAggregateType());
            assertEquals("tax-inv-to-domain", domainEvent.getAggregateId());
            assertEquals("XmlSigningRequestedEvent", domainEvent.getEventType());
            assertEquals("{\"signing\":true}", domainEvent.getPayload());
            assertEquals(now, domainEvent.getCreatedAt());
            assertEquals(OutboxStatus.PUBLISHED, domainEvent.getStatus());
            assertEquals(1, domainEvent.getRetryCount());
            assertEquals("Temporary error", domainEvent.getErrorMessage());
            assertEquals("xml.signing.requested", domainEvent.getTopic());
            assertEquals("tax-inv-to-domain", domainEvent.getPartitionKey());
            assertEquals("{\"key\":\"value\"}", domainEvent.getHeaders());
        }

        @Test
        @DisplayName("Should support round-trip conversion")
        void testRoundTripConversion() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEvent originalDomain = OutboxEvent.builder()
                .id(id)
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId("tax-inv-roundtrip")
                .eventType("TaxInvoiceProcessedEvent")
                .payload("{\"roundtrip\":true}")
                .createdAt(now)
                .publishedAt(now.plusSeconds(15))
                .status(OutboxStatus.PUBLISHED)
                .retryCount(0)
                .errorMessage(null)
                .topic("taxinvoice.processed")
                .partitionKey("tax-inv-roundtrip")
                .headers("{\"traceId\":\"trace-789\"}")
                .build();

            // Act
            OutboxEventEntity entity = OutboxEventEntity.fromDomain(originalDomain);
            OutboxEvent restoredDomain = entity.toDomain();

            // Assert
            assertEquals(originalDomain, restoredDomain, "Round-trip conversion should preserve all fields");
        }

        @Test
        @DisplayName("Should convert from domain with null optional fields")
        void testFromDomainWithNullOptionalFields() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEvent domainEvent = OutboxEvent.builder()
                .id(id)
                .aggregateType("ProcessedTaxInvoice")
                .aggregateId("tax-inv-minimal")
                .eventType("TaxInvoiceProcessedEvent")
                .payload("{\"minimal\":true}")
                .createdAt(now)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .publishedAt(null)
                .errorMessage(null)
                .topic(null)
                .partitionKey(null)
                .headers(null)
                .build();

            // Act
            OutboxEventEntity entity = OutboxEventEntity.fromDomain(domainEvent);

            // Assert
            assertNotNull(entity);
            assertEquals(id, entity.getId());
            assertEquals("ProcessedTaxInvoice", entity.getAggregateType());
            assertEquals("tax-inv-minimal", entity.getAggregateId());
            assertEquals("TaxInvoiceProcessedEvent", entity.getEventType());
            assertEquals("{\"minimal\":true}", entity.getPayload());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(OutboxStatus.PENDING, entity.getStatus());
            assertEquals(0, entity.getRetryCount());
            assertNull(entity.getPublishedAt());
            assertNull(entity.getErrorMessage());
            assertNull(entity.getTopic());
            assertNull(entity.getPartitionKey());
            assertNull(entity.getHeaders());
        }
    }

    @Nested
    @DisplayName("Getters and Setters Tests")
    class GettersSettersTests {

        @Test
        @DisplayName("Should set and get all fields")
        void testSettersAndGetters() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            OutboxEventEntity entity = new OutboxEventEntity();

            // Act
            entity.setId(id);
            entity.setAggregateType("ProcessedTaxInvoice");
            entity.setAggregateId("tax-inv-setters");
            entity.setEventType("TaxInvoiceProcessedEvent");
            entity.setPayload("{\"amount\":1000}");
            entity.setCreatedAt(now);
            entity.setPublishedAt(now.plusSeconds(5));
            entity.setStatus(OutboxStatus.PUBLISHED);
            entity.setRetryCount(0);
            entity.setErrorMessage(null);
            entity.setTopic("taxinvoice.processed");
            entity.setPartitionKey("tax-inv-setters");
            entity.setHeaders("{\"key\":\"value\"}");

            // Assert
            assertEquals(id, entity.getId());
            assertEquals("ProcessedTaxInvoice", entity.getAggregateType());
            assertEquals("tax-inv-setters", entity.getAggregateId());
            assertEquals("TaxInvoiceProcessedEvent", entity.getEventType());
            assertEquals("{\"amount\":1000}", entity.getPayload());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(now.plusSeconds(5), entity.getPublishedAt());
            assertEquals(OutboxStatus.PUBLISHED, entity.getStatus());
            assertEquals(0, entity.getRetryCount());
            assertNull(entity.getErrorMessage());
            assertEquals("taxinvoice.processed", entity.getTopic());
            assertEquals("tax-inv-setters", entity.getPartitionKey());
            assertEquals("{\"key\":\"value\"}", entity.getHeaders());
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create entity with no-args constructor")
        void testNoArgsConstructor() {
            // Act
            OutboxEventEntity entity = new OutboxEventEntity();

            // Assert
            assertNotNull(entity);
            assertNull(entity.getId());
            assertNull(entity.getAggregateType());
            assertNull(entity.getAggregateId());
            assertNull(entity.getEventType());
            assertNull(entity.getPayload());
        }

        @Test
        @DisplayName("Should create entity with all-args constructor")
        void testAllArgsConstructor() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // Act
            OutboxEventEntity entity = new OutboxEventEntity(
                id,
                "ProcessedTaxInvoice",
                "tax-inv-allargs",
                "TaxInvoiceProcessedEvent",
                "{\"allargs\":true}",
                now,
                now.plusSeconds(5),
                OutboxStatus.PUBLISHED,
                0,
                null,
                "taxinvoice.processed",
                "tax-inv-allargs",
                "{\"headers\":true}"
            );

            // Assert
            assertNotNull(entity);
            assertEquals(id, entity.getId());
            assertEquals("ProcessedTaxInvoice", entity.getAggregateType());
            assertEquals("tax-inv-allargs", entity.getAggregateId());
            assertEquals("TaxInvoiceProcessedEvent", entity.getEventType());
            assertEquals("{\"allargs\":true}", entity.getPayload());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(now.plusSeconds(5), entity.getPublishedAt());
            assertEquals(OutboxStatus.PUBLISHED, entity.getStatus());
            assertEquals(0, entity.getRetryCount());
            assertNull(entity.getErrorMessage());
            assertEquals("taxinvoice.processed", entity.getTopic());
            assertEquals("tax-inv-allargs", entity.getPartitionKey());
            assertEquals("{\"headers\":true}", entity.getHeaders());
        }
    }
}
