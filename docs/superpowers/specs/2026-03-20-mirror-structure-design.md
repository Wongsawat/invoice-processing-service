# Mirror Structure from taxinvoice-processing-service

**Date:** 2026-03-20
**Service:** invoice-processing-service
**Reference:** taxinvoice-processing-service

## Overview

Pure structural refactor — no behavioral changes. Aligns `invoice-processing-service` package layout, class names, and `pom.xml` with the hexagonal architecture as implemented in `taxinvoice-processing-service`. All changes are mechanical moves and renames; runtime behavior and Kafka topic names are untouched.

## Approach

Option A: Direct migration — single pass using `git mv` for all moves, followed by import updates across affected files. One commit per logical group. Tests verify no references are missed.

## Changes

### 1. Package/Directory Restructuring

Three infrastructure sub-packages relocated into the `adapter/out/` hierarchy:

| Current path | Target path |
|---|---|
| `infrastructure/persistence/` | `infrastructure/adapter/out/persistence/` |
| `infrastructure/persistence/outbox/` | `infrastructure/adapter/out/persistence/outbox/` |
| `infrastructure/service/` | `infrastructure/adapter/out/parsing/` |

The `infrastructure/adapter/in/messaging/` and `infrastructure/adapter/out/messaging/` packages already match the reference structure — no move needed.

### 2. Class Renames

| Current name | New name | Package |
|---|---|---|
| `InvoiceRouteConfig` | `SagaRouteConfig` | `infrastructure/adapter/in/messaging/` |
| `EventPublisher` | `InvoiceEventPublisher` | `infrastructure/adapter/out/messaging/` |
| `InvoiceReplyEvent` (from `domain/event/`) | `InvoiceReplyEvent` | `infrastructure/adapter/out/messaging/dto/` |

`InvoiceReplyEvent` is also relocated out of the domain layer into the infrastructure messaging DTO package — it is a Kafka wire DTO, not a domain concept.

### 3. Test File Reorganization and Renames

Test files mirror the main source restructuring exactly:

| Current test path | Target test path |
|---|---|
| `infrastructure/persistence/ProcessedInvoiceEntityTest.java` | `infrastructure/adapter/out/persistence/ProcessedInvoiceEntityTest.java` |
| `infrastructure/persistence/ProcessedInvoiceMapperTest.java` | `infrastructure/adapter/out/persistence/ProcessedInvoiceMapperTest.java` |
| `infrastructure/persistence/ProcessedInvoiceRepositoryImplTest.java` | `infrastructure/adapter/out/persistence/ProcessedInvoiceRepositoryImplTest.java` |
| `infrastructure/persistence/InvoicePartyEntityTest.java` | `infrastructure/adapter/out/persistence/InvoicePartyEntityTest.java` |
| `infrastructure/persistence/InvoiceLineItemEntityTest.java` | `infrastructure/adapter/out/persistence/InvoiceLineItemEntityTest.java` |
| `infrastructure/persistence/outbox/OutboxEventEntityTest.java` | `infrastructure/adapter/out/persistence/outbox/OutboxEventEntityTest.java` |
| `infrastructure/persistence/outbox/JpaOutboxEventRepositoryTest.java` | `infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepositoryTest.java` |
| `infrastructure/persistence/outbox/OutboxCleanupSchedulerTest.java` | `infrastructure/adapter/out/persistence/outbox/OutboxCleanupSchedulerTest.java` |
| `infrastructure/service/InvoiceParserServiceImplTest.java` | `infrastructure/adapter/out/parsing/InvoiceParserServiceImplTest.java` |
| `infrastructure/config/InvoiceRouteConfigTest.java` | `infrastructure/adapter/in/messaging/SagaRouteConfigTest.java` |
| `infrastructure/adapter/out/messaging/EventPublisherTest.java` | `infrastructure/adapter/out/messaging/InvoiceEventPublisherTest.java` |

### 4. `pom.xml` Cleanup

Remove unused MapStruct declarations (mapper is manual, matching taxinvoice-processing-service):

- Remove `org.mapstruct:mapstruct` dependency
- Remove `mapstruct-processor` from `maven-compiler-plugin` annotation processor paths

Lombok annotation processor is retained.

## What Does NOT Change

- Kafka topic names (`saga.command.invoice`, `saga.reply.invoice`, etc.)
- Database table names and Flyway migrations
- All business logic, domain model, value objects, state machine
- Spring Boot configuration in `application.yml`
- JaCoCo coverage threshold (85%)
- Consumer group IDs, Camel retry configuration

## Verification

Run `mvn test` after all moves and import updates to confirm zero broken references. All existing tests must pass without modification to test logic.
