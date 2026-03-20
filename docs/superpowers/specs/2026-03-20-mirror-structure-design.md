# Mirror Structure from taxinvoice-processing-service

**Date:** 2026-03-20
**Service:** invoice-processing-service
**Reference:** taxinvoice-processing-service

## Overview

Pure structural refactor — no behavioral changes. Aligns `invoice-processing-service` package layout, class names, and `pom.xml` with the hexagonal architecture as implemented in `taxinvoice-processing-service`. All changes are mechanical moves and renames; runtime behavior and Kafka topic names are untouched.

**Note:** All moved files have their `package` declarations updated to match the new path. Files that are renamed also have all internal class-name references updated (field types, constructor calls, import statements). These are part of the move/rename, not logic changes.

## Approach

Option A: Direct migration — single pass using `git mv` for all moves, followed by import updates across affected files. Three commits, in this order:

1. `pom.xml` cleanup (remove MapStruct) — build must stay green
2. Main source moves, renames, and all corresponding test moves/renames together — build must be green at end of this combined commit
3. Verify with `mvn test`

Main source and test changes are combined in commit 2 because renaming a production class (e.g. `EventPublisher` → `InvoiceEventPublisher`) immediately breaks test files that reference it by name; splitting them across commits would leave the build broken between commits.

## Changes

### 1. Package/Directory Restructuring

Three infrastructure sub-packages relocated into the `adapter/out/` hierarchy:

| Current path | Target path |
|---|---|
| `infrastructure/persistence/` | `infrastructure/adapter/out/persistence/` |
| `infrastructure/persistence/outbox/` | `infrastructure/adapter/out/persistence/outbox/` |
| `infrastructure/service/` | `infrastructure/adapter/out/parsing/` |

The `infrastructure/adapter/in/messaging/` and `infrastructure/adapter/out/messaging/` directory layout already matches the reference structure — only class renames within those packages are needed (see Section 2).

`infrastructure/config/KafkaTopicsProperties` is unchanged and stays in `infrastructure/config/`.

### 2. Class Renames (main source)

| Current name | New name | Package |
|---|---|---|
| `InvoiceRouteConfig` | `SagaRouteConfig` | `infrastructure/adapter/in/messaging/` |
| `EventPublisher` | `InvoiceEventPublisher` | `infrastructure/adapter/out/messaging/` |
| `InvoiceReplyEvent` (moved from `domain/event/`) | `InvoiceReplyEvent` | `infrastructure/adapter/out/messaging/dto/` |

`InvoiceReplyEvent` is also relocated out of the domain layer into the infrastructure messaging DTO package — it is a Kafka wire DTO, not a domain concept.

**Files requiring content updates** as part of this commit:
- `SagaReplyPublisher.java` — imports `InvoiceReplyEvent` from its current `domain.event` path; must be updated to `infrastructure.adapter.out.messaging.dto`
- `InvoiceProcessingServiceApplicationTest.java` — contains hard-coded Spring bean name strings that must be updated: `"eventPublisher"` → `"invoiceEventPublisher"` and `"invoiceRouteConfig"` → `"sagaRouteConfig"` (Spring derives default bean names from class names)
- Any other class importing `domain.event.InvoiceReplyEvent` or `InvoiceRouteConfig` or `EventPublisher`

**Bean name note:** Renaming `EventPublisher` to `InvoiceEventPublisher` changes Spring's default bean name from `eventPublisher` to `invoiceEventPublisher`. Verify no injection site uses a string bean name or `@Qualifier` referencing the old name.

### 3. Test File Reorganization and Renames

Test files mirror the main source restructuring exactly. Package declarations, class declarations, and all internal class-name references (field types, constructor calls, `@MockBean` types) are updated to match the renamed/moved production class. Specifically: `InvoiceRouteConfigTest` class declaration becomes `SagaRouteConfigTest`, and `EventPublisherTest` class declaration becomes `InvoiceEventPublisherTest`.

`InvoiceReplyEventTest` follows the reference service pattern — the reference keeps `TaxInvoiceReplyEventTest` in `domain/event/`, so `InvoiceReplyEventTest` stays in `domain/event/` and is **not** moved.

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
| `application/service/SagaCommandHandlerTest.java` | `infrastructure/adapter/in/messaging/SagaCommandHandlerTest.java` |
| `domain/event/InvoiceReplyEventTest.java` | stays at `domain/event/InvoiceReplyEventTest.java` (no move, but requires import added: `import com.wpanther.invoice.processing.infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent;` — same-package resolution breaks after production class is moved) |
| `InvoiceProcessingServiceApplicationTest.java` | stays at root test package (no move), but requires two bean name strings updated: `"eventPublisher"` → `"invoiceEventPublisher"` and `"invoiceRouteConfig"` → `"sagaRouteConfig"` |

### 4. `pom.xml` Cleanup

Remove unused MapStruct declarations (mapper is manual, matching taxinvoice-processing-service):

- Remove `<mapstruct.version>` from `<properties>` block
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
- `infrastructure/config/KafkaTopicsProperties` — unchanged, stays in place
- `domain/event/InvoiceReplyEventTest.java` — unchanged, stays in place (mirrors reference service pattern)

## Verification

Run `mvn test` after all moves and import updates to confirm zero broken references. All existing tests must pass without modification to test logic.
