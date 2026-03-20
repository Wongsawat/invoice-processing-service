# Mirror Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align `invoice-processing-service` package layout, class names, and `pom.xml` with `taxinvoice-processing-service` (hexagonal adapter structure), with no behavioral changes.

**Architecture:** Pure structural refactor in two commits: (1) pom.xml MapStruct removal, (2) all file moves, renames, and import fixes in one atomic commit so the build stays green end-to-end.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Maven, git mv

---

## File Map

### Main source — moving to new packages

| File (current) | File (target) | Change |
|---|---|---|
| `infrastructure/persistence/ProcessedInvoiceEntity.java` | `infrastructure/adapter/out/persistence/ProcessedInvoiceEntity.java` | package decl |
| `infrastructure/persistence/ProcessedInvoiceMapper.java` | `infrastructure/adapter/out/persistence/ProcessedInvoiceMapper.java` | package decl |
| `infrastructure/persistence/ProcessedInvoiceRepositoryImpl.java` | `infrastructure/adapter/out/persistence/ProcessedInvoiceRepositoryImpl.java` | package decl |
| `infrastructure/persistence/JpaProcessedInvoiceRepository.java` | `infrastructure/adapter/out/persistence/JpaProcessedInvoiceRepository.java` | package decl |
| `infrastructure/persistence/InvoicePartyEntity.java` | `infrastructure/adapter/out/persistence/InvoicePartyEntity.java` | package decl |
| `infrastructure/persistence/InvoiceLineItemEntity.java` | `infrastructure/adapter/out/persistence/InvoiceLineItemEntity.java` | package decl |
| `infrastructure/persistence/outbox/OutboxEventEntity.java` | `infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.java` | package decl |
| `infrastructure/persistence/outbox/JpaOutboxEventRepository.java` | `infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepository.java` | package decl |
| `infrastructure/persistence/outbox/SpringDataOutboxRepository.java` | `infrastructure/adapter/out/persistence/outbox/SpringDataOutboxRepository.java` | package decl |
| `infrastructure/persistence/outbox/OutboxCleanupScheduler.java` | `infrastructure/adapter/out/persistence/outbox/OutboxCleanupScheduler.java` | package decl |
| `infrastructure/service/InvoiceParserServiceImpl.java` | `infrastructure/adapter/out/parsing/InvoiceParserServiceImpl.java` | package decl |
| `domain/event/InvoiceReplyEvent.java` | `infrastructure/adapter/out/messaging/dto/InvoiceReplyEvent.java` | package decl + SagaReplyPublisher import |

### Main source — renamed in place

| File | Change |
|---|---|
| `infrastructure/adapter/in/messaging/InvoiceRouteConfig.java` → `SagaRouteConfig.java` | class decl + InvoiceProcessingServiceApplicationTest bean string |
| `infrastructure/adapter/out/messaging/EventPublisher.java` → `InvoiceEventPublisher.java` | class decl + InvoiceProcessingServiceApplicationTest bean string |

### Main source — content-only updates (no move)

| File | Change |
|---|---|
| `infrastructure/adapter/out/messaging/SagaReplyPublisher.java` | update import: `domain.event.InvoiceReplyEvent` → `infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent` |

### Test files — moving to mirror main source

| File (current) | File (target) |
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

### Test files — content-only updates (no move)

| File | Change |
|---|---|
| `domain/event/InvoiceReplyEventTest.java` | add import: `import com.wpanther.invoice.processing.infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent;` |
| `InvoiceProcessingServiceApplicationTest.java` | `"eventPublisher"` → `"invoiceEventPublisher"`, `"invoiceRouteConfig"` → `"sagaRouteConfig"` |

> **Note:** `"InvoiceReplyEvent"` string literals in `OutboxEventEntityTest` and `JpaOutboxEventRepositoryTest` are Debezium event-type data values, not Java class references — they do NOT change.

---

## Task 1: Remove MapStruct from pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Remove `<mapstruct.version>` from the `<properties>` block**

  Find and delete this line in `pom.xml`:
  ```xml
  <mapstruct.version>1.5.5.Final</mapstruct.version>
  ```

- [ ] **Step 2: Remove the `org.mapstruct:mapstruct` dependency**

  Find and delete this entire block in `pom.xml`:
  ```xml
  <dependency>
      <groupId>org.mapstruct</groupId>
      <artifactId>mapstruct</artifactId>
      <version>${mapstruct.version}</version>
  </dependency>
  ```

- [ ] **Step 3: Remove `mapstruct-processor` from the annotation processor paths**

  In the `maven-compiler-plugin` configuration, find and delete this entire `<path>` block (the one referencing `mapstruct-processor`):
  ```xml
  <path>
      <groupId>org.mapstruct</groupId>
      <artifactId>mapstruct-processor</artifactId>
      <version>${mapstruct.version}</version>
  </path>
  ```
  The Lombok `<path>` block stays.

- [ ] **Step 4: Verify the build compiles**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  mvn compile -DskipTests
  ```
  Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

  ```bash
  git add pom.xml
  git commit -m "Remove unused MapStruct dependency from pom.xml"
  ```

---

## Task 2: Move persistence main source files

**Files:**
- Move: `src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/` → `src/main/java/com/wpanther/invoice/processing/infrastructure/adapter/out/persistence/`

All 10 files in this task update their `package` declaration from `com.wpanther.invoice.processing.infrastructure.persistence` (or `.persistence.outbox`) to the new path. No imports change — these files only reference each other (same package before and after the move).

- [ ] **Step 1: Create target directories and move the 6 top-level persistence files**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  S=src/main/java/com/wpanther/invoice/processing
  mkdir -p $S/infrastructure/adapter/out/persistence/outbox

  git mv $S/infrastructure/persistence/ProcessedInvoiceEntity.java        $S/infrastructure/adapter/out/persistence/
  git mv $S/infrastructure/persistence/ProcessedInvoiceMapper.java         $S/infrastructure/adapter/out/persistence/
  git mv $S/infrastructure/persistence/ProcessedInvoiceRepositoryImpl.java $S/infrastructure/adapter/out/persistence/
  git mv $S/infrastructure/persistence/JpaProcessedInvoiceRepository.java  $S/infrastructure/adapter/out/persistence/
  git mv $S/infrastructure/persistence/InvoicePartyEntity.java             $S/infrastructure/adapter/out/persistence/
  git mv $S/infrastructure/persistence/InvoiceLineItemEntity.java          $S/infrastructure/adapter/out/persistence/
  ```

- [ ] **Step 2: Move the 4 outbox files**

  ```bash
  git mv $S/infrastructure/persistence/outbox/OutboxEventEntity.java          $S/infrastructure/adapter/out/persistence/outbox/
  git mv $S/infrastructure/persistence/outbox/JpaOutboxEventRepository.java   $S/infrastructure/adapter/out/persistence/outbox/
  git mv $S/infrastructure/persistence/outbox/SpringDataOutboxRepository.java $S/infrastructure/adapter/out/persistence/outbox/
  git mv $S/infrastructure/persistence/outbox/OutboxCleanupScheduler.java     $S/infrastructure/adapter/out/persistence/outbox/
  ```

- [ ] **Step 3: Update package declarations in the 6 top-level files**

  In each of these 6 files, change line 1:
  ```java
  // FROM:
  package com.wpanther.invoice.processing.infrastructure.persistence;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;
  ```
  Files to update:
  - `$S/infrastructure/adapter/out/persistence/ProcessedInvoiceEntity.java`
  - `$S/infrastructure/adapter/out/persistence/ProcessedInvoiceMapper.java`
  - `$S/infrastructure/adapter/out/persistence/ProcessedInvoiceRepositoryImpl.java`
  - `$S/infrastructure/adapter/out/persistence/JpaProcessedInvoiceRepository.java`
  - `$S/infrastructure/adapter/out/persistence/InvoicePartyEntity.java`
  - `$S/infrastructure/adapter/out/persistence/InvoiceLineItemEntity.java`

- [ ] **Step 4: Update package declarations in the 4 outbox files**

  In each of these 4 files, change line 1:
  ```java
  // FROM:
  package com.wpanther.invoice.processing.infrastructure.persistence.outbox;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence.outbox;
  ```
  Files to update:
  - `$S/infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.java`
  - `$S/infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepository.java`
  - `$S/infrastructure/adapter/out/persistence/outbox/SpringDataOutboxRepository.java`
  - `$S/infrastructure/adapter/out/persistence/outbox/OutboxCleanupScheduler.java`

---

## Task 3: Move parsing main source file

**Files:**
- Move: `infrastructure/service/InvoiceParserServiceImpl.java` → `infrastructure/adapter/out/parsing/InvoiceParserServiceImpl.java`

- [ ] **Step 1: Move the file**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  S=src/main/java/com/wpanther/invoice/processing
  mkdir -p $S/infrastructure/adapter/out/parsing

  git mv $S/infrastructure/service/InvoiceParserServiceImpl.java \
         $S/infrastructure/adapter/out/parsing/
  ```

- [ ] **Step 2: Update package declaration**

  In `$S/infrastructure/adapter/out/parsing/InvoiceParserServiceImpl.java`, change line 1:
  ```java
  // FROM:
  package com.wpanther.invoice.processing.infrastructure.service;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.out.parsing;
  ```

---

## Task 4: Move InvoiceReplyEvent + update SagaReplyPublisher import

**Files:**
- Move: `domain/event/InvoiceReplyEvent.java` → `infrastructure/adapter/out/messaging/dto/InvoiceReplyEvent.java`
- Modify: `infrastructure/adapter/out/messaging/SagaReplyPublisher.java`

- [ ] **Step 1: Move InvoiceReplyEvent to the dto package**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  S=src/main/java/com/wpanther/invoice/processing
  mkdir -p $S/infrastructure/adapter/out/messaging/dto

  git mv $S/domain/event/InvoiceReplyEvent.java \
         $S/infrastructure/adapter/out/messaging/dto/
  ```

- [ ] **Step 2: Update package declaration in InvoiceReplyEvent.java**

  In `$S/infrastructure/adapter/out/messaging/dto/InvoiceReplyEvent.java`, change line 1:
  ```java
  // FROM:
  package com.wpanther.invoice.processing.domain.event;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging.dto;
  ```

- [ ] **Step 3: Update import in SagaReplyPublisher.java**

  In `$S/infrastructure/adapter/out/messaging/SagaReplyPublisher.java`, update line 3:
  ```java
  // FROM:
  import com.wpanther.invoice.processing.domain.event.InvoiceReplyEvent;
  // TO:
  import com.wpanther.invoice.processing.infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent;
  ```

---

## Task 5: Rename InvoiceRouteConfig → SagaRouteConfig

**Files:**
- Rename: `infrastructure/adapter/in/messaging/InvoiceRouteConfig.java` → `SagaRouteConfig.java`

- [ ] **Step 1: Rename the file**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  S=src/main/java/com/wpanther/invoice/processing

  git mv $S/infrastructure/adapter/in/messaging/InvoiceRouteConfig.java \
         $S/infrastructure/adapter/in/messaging/SagaRouteConfig.java
  ```

- [ ] **Step 2: Update the class declaration inside the file**

  In `$S/infrastructure/adapter/in/messaging/SagaRouteConfig.java`, find and replace:
  ```java
  // FROM:
  public class InvoiceRouteConfig extends RouteBuilder {
  // TO:
  public class SagaRouteConfig extends RouteBuilder {
  ```

---

## Task 6: Rename EventPublisher → InvoiceEventPublisher

**Files:**
- Rename: `infrastructure/adapter/out/messaging/EventPublisher.java` → `InvoiceEventPublisher.java`

- [ ] **Step 1: Rename the file**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  S=src/main/java/com/wpanther/invoice/processing

  git mv $S/infrastructure/adapter/out/messaging/EventPublisher.java \
         $S/infrastructure/adapter/out/messaging/InvoiceEventPublisher.java
  ```

- [ ] **Step 2: Update the class declaration inside the file**

  In `$S/infrastructure/adapter/out/messaging/InvoiceEventPublisher.java`, find and replace:
  ```java
  // FROM:
  public class EventPublisher implements InvoiceEventPublishingPort {
  // TO:
  public class InvoiceEventPublisher implements InvoiceEventPublishingPort {
  ```

---

## Task 7: Move persistence test files

**Files:**
- Move: `src/test/.../infrastructure/persistence/` → `src/test/.../infrastructure/adapter/out/persistence/`

- [ ] **Step 1: Create target test directories and move 5 top-level persistence test files**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  T=src/test/java/com/wpanther/invoice/processing
  mkdir -p $T/infrastructure/adapter/out/persistence/outbox

  git mv $T/infrastructure/persistence/ProcessedInvoiceEntityTest.java        $T/infrastructure/adapter/out/persistence/
  git mv $T/infrastructure/persistence/ProcessedInvoiceMapperTest.java         $T/infrastructure/adapter/out/persistence/
  git mv $T/infrastructure/persistence/ProcessedInvoiceRepositoryImplTest.java $T/infrastructure/adapter/out/persistence/
  git mv $T/infrastructure/persistence/InvoicePartyEntityTest.java             $T/infrastructure/adapter/out/persistence/
  git mv $T/infrastructure/persistence/InvoiceLineItemEntityTest.java          $T/infrastructure/adapter/out/persistence/
  ```

- [ ] **Step 2: Move 3 outbox test files**

  ```bash
  git mv $T/infrastructure/persistence/outbox/OutboxEventEntityTest.java       $T/infrastructure/adapter/out/persistence/outbox/
  git mv $T/infrastructure/persistence/outbox/JpaOutboxEventRepositoryTest.java $T/infrastructure/adapter/out/persistence/outbox/
  git mv $T/infrastructure/persistence/outbox/OutboxCleanupSchedulerTest.java  $T/infrastructure/adapter/out/persistence/outbox/
  ```

- [ ] **Step 3: Update package declarations in the 5 top-level test files**

  In each file, change the `package` declaration:
  ```java
  // FROM:
  package com.wpanther.invoice.processing.infrastructure.persistence;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;
  ```

- [ ] **Step 4: Update package declarations in the 3 outbox test files**

  ```java
  // FROM:
  package com.wpanther.invoice.processing.infrastructure.persistence.outbox;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence.outbox;
  ```

---

## Task 8: Move remaining test files

**Files:**
- Move: `infrastructure/service/InvoiceParserServiceImplTest.java` → `infrastructure/adapter/out/parsing/`
- Move+rename: `infrastructure/config/InvoiceRouteConfigTest.java` → `infrastructure/adapter/in/messaging/SagaRouteConfigTest.java`
- Rename: `infrastructure/adapter/out/messaging/EventPublisherTest.java` → `InvoiceEventPublisherTest.java`
- Move: `application/service/SagaCommandHandlerTest.java` → `infrastructure/adapter/in/messaging/`

- [ ] **Step 1: Move InvoiceParserServiceImplTest**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  T=src/test/java/com/wpanther/invoice/processing
  mkdir -p $T/infrastructure/adapter/out/parsing
  mkdir -p $T/infrastructure/adapter/in/messaging

  git mv $T/infrastructure/service/InvoiceParserServiceImplTest.java \
         $T/infrastructure/adapter/out/parsing/
  ```

  Update package declaration:
  ```java
  // FROM:
  package com.wpanther.invoice.processing.infrastructure.service;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.out.parsing;
  ```

- [ ] **Step 2: Move and rename InvoiceRouteConfigTest → SagaRouteConfigTest**

  ```bash
  git mv $T/infrastructure/config/InvoiceRouteConfigTest.java \
         $T/infrastructure/adapter/in/messaging/SagaRouteConfigTest.java
  ```

  In the file, update:
  - Package declaration:
    ```java
    // FROM:
    package com.wpanther.invoice.processing.infrastructure.config;
    // TO:
    package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;
    ```
  - Class declaration:
    ```java
    // FROM:
    class InvoiceRouteConfigTest {
    // TO:
    class SagaRouteConfigTest {
    ```
  - Any reference to `InvoiceRouteConfig` inside the test body → `SagaRouteConfig` (check imports too)

- [ ] **Step 3: Rename EventPublisherTest → InvoiceEventPublisherTest**

  ```bash
  git mv $T/infrastructure/adapter/out/messaging/EventPublisherTest.java \
         $T/infrastructure/adapter/out/messaging/InvoiceEventPublisherTest.java
  ```

  In the file, update:
  - Class declaration:
    ```java
    // FROM:
    class EventPublisherTest {
    // TO:
    class InvoiceEventPublisherTest {
    ```
  - Field type declaration (e.g. `private EventPublisher eventPublisher;` → `private InvoiceEventPublisher eventPublisher;`)
  - Constructor call (e.g. `new EventPublisher(...)` → `new InvoiceEventPublisher(...)`)
  - Any import of `EventPublisher` → import `InvoiceEventPublisher`

- [ ] **Step 4: Move SagaCommandHandlerTest**

  ```bash
  git mv $T/application/service/SagaCommandHandlerTest.java \
         $T/infrastructure/adapter/in/messaging/
  ```

  Update package declaration:
  ```java
  // FROM:
  package com.wpanther.invoice.processing.application.service;
  // TO:
  package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;
  ```

---

## Task 9: Update non-moved test files

**Files:**
- Modify: `src/test/.../domain/event/InvoiceReplyEventTest.java`
- Modify: `src/test/.../InvoiceProcessingServiceApplicationTest.java`

- [ ] **Step 1: Add import to InvoiceReplyEventTest.java**

  In `src/test/java/com/wpanther/invoice/processing/domain/event/InvoiceReplyEventTest.java`, add this import (the class is no longer in the same package):
  ```java
  import com.wpanther.invoice.processing.infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent;
  ```

- [ ] **Step 2: Update bean name strings in InvoiceProcessingServiceApplicationTest.java**

  In `src/test/java/com/wpanther/invoice/processing/InvoiceProcessingServiceApplicationTest.java`, update lines 38 and 40:
  ```java
  // FROM:
  assertTrue(applicationContext.containsBean("eventPublisher"),
      "Should have EventPublisher bean");
  assertTrue(applicationContext.containsBean("invoiceRouteConfig"),
      "Should have InvoiceRouteConfig bean");

  // TO:
  assertTrue(applicationContext.containsBean("invoiceEventPublisher"),
      "Should have InvoiceEventPublisher bean");
  assertTrue(applicationContext.containsBean("sagaRouteConfig"),
      "Should have SagaRouteConfig bean");
  ```

---

## Task 10: Verify, then commit all structural changes

- [ ] **Step 1: Confirm no old package references remain**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
  grep -r "infrastructure\.persistence\|infrastructure\.service" src --include="*.java"
  ```
  Expected: no output (zero matches)

- [ ] **Step 2: Confirm no old class name references remain**

  ```bash
  grep -r "InvoiceRouteConfig\b\|EventPublisher\b" src --include="*.java" \
    | grep -v "InvoiceEventPublisher\|SagaRouteConfig\|InvoiceEventPublishingPort"
  ```
  Expected: no output

- [ ] **Step 3: Run the full test suite**

  ```bash
  mvn test
  ```
  Expected: `BUILD SUCCESS`, all tests pass

- [ ] **Step 4: Commit all structural changes**

  ```bash
  git add -A
  git commit -m "Mirror hexagonal adapter structure from taxinvoice-processing-service"
  ```
