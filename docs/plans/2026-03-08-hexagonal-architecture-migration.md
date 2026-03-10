# Hexagonal Architecture Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate `invoice-processing-service` to textbook Hexagonal Architecture (Ports and Adapters) following a layer-by-layer incremental strategy that keeps tests green after every commit.

**Architecture:** Domain layer has only outbound ports (`domain/port/out/`). Application layer has both inbound ports (`application/port/in/` — use case interfaces) and outbound ports (`application/port/out/` — orchestration infrastructure). Infrastructure is split into `adapter/in/` (driving: Camel routes, command handler) and `adapter/out/` (driven: JPA, outbox publishers, XML parser). The aggregate raises domain events that the application layer drains and routes through the outbound event port. The existing `DataIntegrityViolationException` race condition handler is preserved in the use case implementation.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, PostgreSQL, Kafka, JPA/Hibernate, Flyway, JaCoCo, JUnit 5, Mockito

---

## Reference: Current → Target File Mapping

| Current path | Target path | Change |
|---|---|---|
| `domain/repository/ProcessedInvoiceRepository` | `domain/port/out/ProcessedInvoiceRepository` | Move |
| `domain/service/InvoiceParserService` | `domain/port/out/InvoiceParserPort` | Move + rename |
| `domain/port/SagaReplyPort` | `application/port/out/SagaReplyPort` | Move |
| `domain/event/ProcessInvoiceCommand` | `infrastructure/adapter/in/messaging/dto/ProcessInvoiceCommand` | Move |
| `domain/event/CompensateInvoiceCommand` | `infrastructure/adapter/in/messaging/dto/CompensateInvoiceCommand` | Move |
| `domain/event/InvoiceReplyEvent` | `infrastructure/adapter/out/messaging/dto/InvoiceReplyEvent` | Move |
| `domain/event/InvoiceProcessedEvent` | `infrastructure/adapter/out/messaging/dto/InvoiceProcessedEvent` | Move |
| `application/service/SagaCommandHandler` | `infrastructure/adapter/in/messaging/SagaCommandHandler` | Move + slim |
| `infrastructure/config/InvoiceRouteConfig` | `infrastructure/adapter/in/messaging/InvoiceRouteConfig` | Move |
| `infrastructure/messaging/EventPublisher` | `infrastructure/adapter/out/messaging/InvoiceEventPublisher` | Move + rename |
| `infrastructure/messaging/SagaReplyPublisher` | `infrastructure/adapter/out/messaging/SagaReplyPublisher` | Move |
| `infrastructure/messaging/HeaderSerializer` | `infrastructure/adapter/out/messaging/HeaderSerializer` | Move |
| `infrastructure/persistence/ProcessedInvoiceRepositoryImpl` | `infrastructure/adapter/out/persistence/ProcessedInvoiceRepositoryAdapter` | Move + rename |
| `infrastructure/persistence/*.java` | `infrastructure/adapter/out/persistence/` | Move |
| `infrastructure/persistence/outbox/*.java` | `infrastructure/adapter/out/outbox/` | Move |
| `infrastructure/service/InvoiceParserServiceImpl` | `infrastructure/adapter/out/parsing/InvoiceParserAdapter` | Move + rename |

**New files created:**
- `domain/event/InvoiceProcessedDomainEvent.java`
- `application/port/in/ProcessInvoiceUseCase.java`
- `application/port/in/CompensateInvoiceUseCase.java`
- `application/port/out/InvoiceEventPublishingPort.java`

**Base package:** `com.wpanther.invoice.processing`
**Base source path:** `src/main/java/com/wpanther/invoice/processing/`
**Base test path:** `src/test/java/com/wpanther/invoice/processing/`

---

## STEP 1: Domain Layer Restructure

> After this step: `domain/port/out/` holds all domain outbound ports. `domain/event/` holds the pure domain event. Old `domain/repository/`, `domain/service/`, `domain/port/` packages are deleted.

---

### Task 1: Create `domain/port/out/ProcessedInvoiceRepository`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/domain/port/out/ProcessedInvoiceRepository.java`
- Delete later (Task 5): `src/main/java/com/wpanther/invoice/processing/domain/repository/ProcessedInvoiceRepository.java`

**Step 1: Create new file — package change only**

```java
package com.wpanther.invoice.processing.domain.port.out;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.ProcessingStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port — persistence contract for ProcessedInvoice aggregate.
 * Domain dictates the contract; infrastructure provides the implementation.
 */
public interface ProcessedInvoiceRepository {

    ProcessedInvoice save(ProcessedInvoice invoice);

    Optional<ProcessedInvoice> findById(InvoiceId id);

    Optional<ProcessedInvoice> findByInvoiceNumber(String invoiceNumber);

    List<ProcessedInvoice> findByStatus(ProcessingStatus status);

    Optional<ProcessedInvoice> findBySourceInvoiceId(String sourceInvoiceId);

    boolean existsByInvoiceNumber(String invoiceNumber);

    void deleteById(InvoiceId id);
}
```

**Step 2: Verify compile**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
mvn compile -q 2>&1 | head -20
```

Expected: success (old interface still exists, no conflicts yet).

---

### Task 2: Create `domain/port/out/InvoiceParserPort`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/domain/port/out/InvoiceParserPort.java`
- Delete later (Task 5): `src/main/java/com/wpanther/invoice/processing/domain/service/InvoiceParserService.java`

**Step 1: Create new interface**

```java
package com.wpanther.invoice.processing.domain.port.out;

import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;

/**
 * Outbound port — XML parsing contract.
 * Domain defines what it needs; the teda-library adapter provides it.
 */
public interface InvoiceParserPort {

    /**
     * Parse XML content into a ProcessedInvoice domain object.
     *
     * @param xmlContent      The raw XML string from the saga command
     * @param sourceInvoiceId The document ID for idempotency tracking
     * @return Parsed domain object in PENDING status
     * @throws InvoiceParsingException if parsing fails
     */
    ProcessedInvoice parse(String xmlContent, String sourceInvoiceId)
            throws InvoiceParsingException;

    /**
     * Checked exception for XML parsing failures.
     */
    class InvoiceParsingException extends Exception {
        public InvoiceParsingException(String message) {
            super(message);
        }

        public InvoiceParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

**Step 2: Verify compile**

```bash
mvn compile -q 2>&1 | head -20
```

Expected: success.

---

### Task 3: Create `domain/event/InvoiceProcessedDomainEvent`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/domain/event/InvoiceProcessedDomainEvent.java`
- Create test: `src/test/java/com/wpanther/invoice/processing/domain/event/InvoiceProcessedDomainEventTest.java`

**Step 1: Write failing test**

```java
package com.wpanther.invoice.processing.domain.event;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceProcessedDomainEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        InvoiceId id = InvoiceId.generate();
        Money total = new Money(new BigDecimal("1000.00"), "THB");
        Instant now = Instant.now();

        InvoiceProcessedDomainEvent event = new InvoiceProcessedDomainEvent(
            id, "INV-001", total, "corr-123", now
        );

        assertThat(event.invoiceId()).isEqualTo(id);
        assertThat(event.invoiceNumber()).isEqualTo("INV-001");
        assertThat(event.total()).isEqualTo(total);
        assertThat(event.correlationId()).isEqualTo("corr-123");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void shouldBeEqualWhenAllFieldsMatch() {
        InvoiceId id = InvoiceId.generate();
        Money total = new Money(new BigDecimal("500.00"), "THB");
        Instant now = Instant.now();

        InvoiceProcessedDomainEvent e1 = new InvoiceProcessedDomainEvent(id, "INV-002", total, "c-1", now);
        InvoiceProcessedDomainEvent e2 = new InvoiceProcessedDomainEvent(id, "INV-002", total, "c-1", now);

        assertThat(e1).isEqualTo(e2);
    }
}
```

**Step 2: Run to confirm failure**

```bash
mvn test -Dtest=InvoiceProcessedDomainEventTest -q 2>&1 | tail -10
```

Expected: FAIL — `InvoiceProcessedDomainEvent` does not exist.

**Step 3: Create the domain event**

```java
package com.wpanther.invoice.processing.domain.event;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.Money;

import java.time.Instant;

/**
 * Domain event raised by ProcessedInvoice when processing completes.
 * Pure Java record — no framework or Kafka dependencies.
 * The application layer translates this into a Kafka DTO via InvoiceEventPublishingPort.
 */
public record InvoiceProcessedDomainEvent(
    InvoiceId invoiceId,
    String invoiceNumber,
    Money total,
    String correlationId,
    Instant occurredAt
) {}
```

**Step 4: Run test to confirm pass**

```bash
mvn test -Dtest=InvoiceProcessedDomainEventTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

---

### Task 4: Add domain event support to `ProcessedInvoice`

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/processing/domain/model/ProcessedInvoice.java`
- Modify test: `src/test/java/com/wpanther/invoice/processing/domain/model/ProcessedInvoiceTest.java`

**Step 1: Write failing tests — add to existing `ProcessedInvoiceTest`**

```java
// Add imports at top of test file:
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;

@Test
void markCompleted_shouldRaiseInvoiceProcessedDomainEvent() {
    ProcessedInvoice invoice = buildValidInvoice();
    invoice.startProcessing();

    invoice.markCompleted("corr-abc");

    assertThat(invoice.domainEvents()).hasSize(1);
    InvoiceProcessedDomainEvent event =
        (InvoiceProcessedDomainEvent) invoice.domainEvents().get(0);
    assertThat(event.invoiceId()).isEqualTo(invoice.getId());
    assertThat(event.correlationId()).isEqualTo("corr-abc");
    assertThat(event.invoiceNumber()).isEqualTo(invoice.getInvoiceNumber());
    assertThat(event.occurredAt()).isNotNull();
}

@Test
void clearDomainEvents_shouldEmptyTheList() {
    ProcessedInvoice invoice = buildValidInvoice();
    invoice.startProcessing();
    invoice.markCompleted("corr-xyz");
    assertThat(invoice.domainEvents()).hasSize(1);

    invoice.clearDomainEvents();

    assertThat(invoice.domainEvents()).isEmpty();
}

@Test
void domainEvents_shouldBeUnmodifiable() {
    ProcessedInvoice invoice = buildValidInvoice();

    assertThatThrownBy(() -> invoice.domainEvents().add(new Object()))
        .isInstanceOf(UnsupportedOperationException.class);
}
```

**Step 2: Run to confirm failure**

```bash
mvn test -Dtest=ProcessedInvoiceTest -q 2>&1 | tail -20
```

Expected: FAIL — `domainEvents()`, `clearDomainEvents()`, `markCompleted(String)` do not exist.

**Step 3: Modify `ProcessedInvoice`**

Add these two imports at the top of the file:

```java
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import java.time.Instant;
```

Add this field after the `errorMessage` field declaration (after line 44):

```java
// Domain events raised during aggregate lifecycle
private final List<Object> domainEvents = new ArrayList<>();
```

Replace the existing `markCompleted()` method (lines 145–151) with:

```java
/**
 * Mark invoice processing as completed.
 * Raises InvoiceProcessedDomainEvent.
 *
 * @param correlationId The saga correlation ID for event tracing
 */
public void markCompleted(String correlationId) {
    if (status != ProcessingStatus.PROCESSING) {
        throw new IllegalStateException("Can only complete from PROCESSING status");
    }
    this.status = ProcessingStatus.COMPLETED;
    this.completedAt = LocalDateTime.now();
    domainEvents.add(new InvoiceProcessedDomainEvent(
        this.id,
        this.invoiceNumber,
        this.getTotal(),
        correlationId,
        Instant.now()
    ));
}
```

Add these two methods after `markFailed()`:

```java
/**
 * Returns an unmodifiable view of domain events raised since last clear.
 */
public List<Object> domainEvents() {
    return Collections.unmodifiableList(domainEvents);
}

/**
 * Clears all domain events. Call after the application layer has processed them.
 */
public void clearDomainEvents() {
    domainEvents.clear();
}
```

**Step 4: Run tests**

```bash
mvn test -Dtest=ProcessedInvoiceTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

**Step 5: Run full suite to catch all `markCompleted()` call sites**

```bash
mvn test -q 2>&1 | tail -20
```

Fix any compilation errors where `markCompleted()` is called without `correlationId`. Check:
- `ProcessedInvoiceMapper.java` — if it reconstructs domain objects via Builder (not calling `markCompleted()`), no change needed
- Any test helpers calling `markCompleted()` — pass `"test-correlation"` as argument

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/invoice/processing/domain/ \
        src/test/java/com/wpanther/invoice/processing/domain/
git commit -m "domain: add port/out packages, domain event, and domain event raising on markCompleted"
```

---

### Task 5: Update implementations to compile against new domain ports; delete old packages

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/processing/infrastructure/service/InvoiceParserServiceImpl.java`
- Modify: `src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/ProcessedInvoiceRepositoryImpl.java`
- Modify: `src/main/java/com/wpanther/invoice/processing/application/service/InvoiceProcessingService.java` (imports only)
- Modify: `src/main/java/com/wpanther/invoice/processing/application/service/SagaCommandHandler.java` (imports only)
- Delete: `src/main/java/com/wpanther/invoice/processing/domain/repository/ProcessedInvoiceRepository.java`
- Delete: `src/main/java/com/wpanther/invoice/processing/domain/service/InvoiceParserService.java`
- Delete: `src/main/java/com/wpanther/invoice/processing/domain/port/SagaReplyPort.java`

**Step 1: Update `InvoiceParserServiceImpl`**

Change:
- `implements InvoiceParserService` → `implements InvoiceParserPort`
- Import: `domain.service.InvoiceParserService` → `domain.port.out.InvoiceParserPort`
- Method: `parseInvoice(...)` → `parse(...)` (rename to match new port)
- Exception: `InvoiceParserService.InvoiceParsingException` → `InvoiceParserPort.InvoiceParsingException`

**Step 2: Update `ProcessedInvoiceRepositoryImpl` import**

```java
// Old:
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
// New:
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
```

**Step 3: Update `InvoiceProcessingService` imports**

```java
// Old:
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
import com.wpanther.invoice.processing.domain.service.InvoiceParserService;
// New:
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
import com.wpanther.invoice.processing.domain.port.out.InvoiceParserPort;
```

Also update field declarations and method calls:
```java
// Old field:
private final InvoiceParserService parserService;
// New field:
private final InvoiceParserPort parserPort;

// Old method call:
ProcessedInvoice invoice = parserService.parseInvoice(xmlContent, documentId);
// New method call:
ProcessedInvoice invoice = parserPort.parse(xmlContent, documentId);

// Old exception reference in throws clause:
throws InvoiceParserService.InvoiceParsingException
// New:
throws InvoiceParserPort.InvoiceParsingException
```

**Step 4: Update `SagaCommandHandler` import**

```java
// Old:
import com.wpanther.invoice.processing.domain.repository.ProcessedInvoiceRepository;
// New:
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
```

**Step 5: Delete old domain packages**

```bash
rm src/main/java/com/wpanther/invoice/processing/domain/repository/ProcessedInvoiceRepository.java
rm src/main/java/com/wpanther/invoice/processing/domain/service/InvoiceParserService.java
rm src/main/java/com/wpanther/invoice/processing/domain/port/SagaReplyPort.java
rmdir src/main/java/com/wpanther/invoice/processing/domain/repository
rmdir src/main/java/com/wpanther/invoice/processing/domain/service
rmdir src/main/java/com/wpanther/invoice/processing/domain/port
```

**Step 6: Compile and fix remaining errors**

```bash
mvn compile -q 2>&1 | head -40
```

**Step 7: Run tests**

```bash
mvn test -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS.

**Step 8: Commit**

```bash
git add -A
git commit -m "domain: migrate implementations to domain/port/out contracts, remove old packages"
```

---

## STEP 2: Application Layer Restructure

> After this step: `application/port/in/` holds use case interfaces. `application/port/out/` holds `SagaReplyPort` and `InvoiceEventPublishingPort`. `InvoiceProcessingService` implements both use cases.

---

### Task 6: Create inbound port `ProcessInvoiceUseCase`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/application/port/in/ProcessInvoiceUseCase.java`

**Step 1: Create the interface**

```java
package com.wpanther.invoice.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port — driving adapter (Camel/Kafka) calls this to process an invoice.
 * Implementation: application/service/InvoiceProcessingService.
 */
public interface ProcessInvoiceUseCase {

    /**
     * Process an invoice from a saga command.
     * Handles idempotency, parses XML, persists, raises domain events, publishes saga reply.
     * Race conditions (DataIntegrityViolationException) are treated as idempotent success.
     *
     * @param documentId    Source document ID (used for idempotency)
     * @param xmlContent    Raw XML string to parse
     * @param sagaId        Saga instance identifier
     * @param sagaStep      Current step in the saga
     * @param correlationId Correlation ID for tracing
     */
    void process(String documentId, String xmlContent,
                 String sagaId, SagaStep sagaStep, String correlationId);
}
```

**Step 2: Compile**

```bash
mvn compile -q 2>&1 | head -20
```

Expected: success.

---

### Task 7: Create inbound port `CompensateInvoiceUseCase`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/application/port/in/CompensateInvoiceUseCase.java`

**Step 1: Create the interface**

```java
package com.wpanther.invoice.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port — driving adapter calls this to compensate (rollback) invoice processing.
 * Implementation: application/service/InvoiceProcessingService.
 */
public interface CompensateInvoiceUseCase {

    /**
     * Compensate (hard delete) a previously processed invoice.
     * Safe to call if the invoice was never processed (no-op).
     *
     * @param documentId    Source document ID identifying the invoice to delete
     * @param sagaId        Saga instance identifier
     * @param sagaStep      Current step in the saga
     * @param correlationId Correlation ID for tracing
     */
    void compensate(String documentId, String sagaId,
                    SagaStep sagaStep, String correlationId);
}
```

**Step 2: Compile**

```bash
mvn compile -q 2>&1 | head -20
```

---

### Task 8: Create outbound port `SagaReplyPort` in `application/port/out/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/application/port/out/SagaReplyPort.java`

Note: the old `domain/port/SagaReplyPort` was deleted in Task 5.

**Step 1: Create the interface**

```java
package com.wpanther.invoice.processing.application.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Outbound port — application layer sends saga reply events to the orchestrator.
 * Implementation: infrastructure/adapter/out/messaging/SagaReplyPublisher.
 */
public interface SagaReplyPort {

    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId);

    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage);

    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
```

**Step 2: Update `SagaReplyPublisher` implements clause**

In `infrastructure/messaging/SagaReplyPublisher.java`:
- Change import: `domain.port.SagaReplyPort` → `application.port.out.SagaReplyPort`
- The `implements SagaReplyPort` clause stays identical

**Step 3: Compile**

```bash
mvn compile -q 2>&1 | head -20
```

---

### Task 9: Create outbound port `InvoiceEventPublishingPort`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/application/port/out/InvoiceEventPublishingPort.java`
- Create test: `src/test/java/com/wpanther/invoice/processing/application/port/out/InvoiceEventPublishingPortTest.java`

**Step 1: Write failing test**

```java
package com.wpanther.invoice.processing.application.port.out;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;

class InvoiceEventPublishingPortTest {

    @Test
    void shouldAcceptDomainEventPublishCall() {
        InvoiceEventPublishingPort port = mock(InvoiceEventPublishingPort.class);
        InvoiceProcessedDomainEvent event = new InvoiceProcessedDomainEvent(
            InvoiceId.generate(), "INV-001",
            new Money(new BigDecimal("100.00"), "THB"),
            "corr-1", Instant.now()
        );

        port.publish(event);

        verify(port).publish(event);
    }
}
```

**Step 2: Run to confirm failure**

```bash
mvn test -Dtest=InvoiceEventPublishingPortTest -q 2>&1 | tail -10
```

Expected: FAIL — interface doesn't exist.

**Step 3: Create the interface**

```java
package com.wpanther.invoice.processing.application.port.out;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;

/**
 * Outbound port — application layer publishes the InvoiceProcessedDomainEvent.
 * Implementation: infrastructure/adapter/out/messaging/InvoiceEventPublisher.
 * The adapter translates the pure domain event into a Kafka DTO before writing to the outbox.
 */
public interface InvoiceEventPublishingPort {

    /**
     * Publish a domain event indicating an invoice was successfully processed.
     * Must be called within an active transaction (MANDATORY propagation on the adapter).
     */
    void publish(InvoiceProcessedDomainEvent event);
}
```

**Step 4: Run test to confirm pass**

```bash
mvn test -Dtest=InvoiceEventPublishingPortTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

**Step 5: Update `EventPublisher` to implement the new port**

In `infrastructure/messaging/EventPublisher.java`:
- Add `implements InvoiceEventPublishingPort` to class declaration
- Add import: `application.port.out.InvoiceEventPublishingPort`
- Rename method `publishInvoiceProcessed(InvoiceProcessedEvent event)` → `publish(InvoiceProcessedDomainEvent domainEvent)` with `@Override`
- Method still builds `InvoiceProcessedEvent` internally from domain event fields

Updated method signature:
```java
@Override
@Transactional(propagation = Propagation.MANDATORY)
public void publish(InvoiceProcessedDomainEvent domainEvent) {
    InvoiceProcessedEvent kafkaEvent = new InvoiceProcessedEvent(
        domainEvent.invoiceId().toString(),
        domainEvent.invoiceNumber(),
        domainEvent.total().amount(),
        domainEvent.total().currency(),
        domainEvent.correlationId()
    );

    Map<String, String> headers = Map.of(
        "correlationId", domainEvent.correlationId(),
        "invoiceNumber", domainEvent.invoiceNumber()
    );

    outboxService.saveWithRouting(
        kafkaEvent,
        "ProcessedInvoice",
        domainEvent.invoiceId().toString(),
        "invoice.processed",
        domainEvent.invoiceId().toString(),
        headerSerializer.toJson(headers)
    );

    log.info("Published InvoiceProcessedEvent to outbox: {}", domainEvent.invoiceNumber());
}
```

Add required imports:
```java
import com.wpanther.invoice.processing.application.port.out.InvoiceEventPublishingPort;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
```

Note: `InvoiceId.toString()` — verify the actual method on `InvoiceId` (may be `getValue().toString()` or directly `toString()` if it wraps UUID). Check `InvoiceId.java` to confirm the correct accessor.

**Step 6: Compile**

```bash
mvn compile -q 2>&1 | head -30
```

**Step 7: Commit**

```bash
git add src/main/java/com/wpanther/invoice/processing/application/port/ \
        src/main/java/com/wpanther/invoice/processing/infrastructure/messaging/ \
        src/test/java/com/wpanther/invoice/processing/application/port/
git commit -m "application: add port/in use case interfaces and port/out event/reply ports"
```

---

### Task 10: Refactor `InvoiceProcessingService` to implement use cases

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/processing/application/service/InvoiceProcessingService.java`
- Modify: `src/test/java/com/wpanther/invoice/processing/application/service/InvoiceProcessingServiceTest.java`

**Step 1: Write failing tests — update `InvoiceProcessingServiceTest`**

Replace mock setup and add new test methods:

```java
// Replace existing @Mock declarations with:
@Mock ProcessedInvoiceRepository invoiceRepository;      // domain/port/out
@Mock InvoiceParserPort parserPort;                      // domain/port/out
@Mock SagaReplyPort sagaReplyPort;                       // application/port/out
@Mock InvoiceEventPublishingPort eventPublishingPort;    // application/port/out

@InjectMocks InvoiceProcessingService service;

// Add these imports:
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.out.SagaReplyPort;
import com.wpanther.invoice.processing.application.port.out.InvoiceEventPublishingPort;
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
import com.wpanther.invoice.processing.domain.port.out.InvoiceParserPort;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.saga.domain.enums.SagaStep;
import org.springframework.dao.DataIntegrityViolationException;
```

Add new test methods:

```java
@Test
void process_shouldPublishSuccessReply_onSuccess() throws Exception {
    String documentId = "doc-001";
    when(invoiceRepository.findBySourceInvoiceId(documentId)).thenReturn(Optional.empty());
    ProcessedInvoice invoice = buildCompletableInvoice(documentId);
    when(parserPort.parse(any(), eq(documentId))).thenReturn(invoice);
    when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.process(documentId, "<xml/>", "saga-001", SagaStep.PROCESS_INVOICE, "corr-001");

    verify(sagaReplyPort).publishSuccess("saga-001", SagaStep.PROCESS_INVOICE, "corr-001");
    verify(eventPublishingPort).publish(any(InvoiceProcessedDomainEvent.class));
}

@Test
void process_shouldPublishFailureReply_onParsingException() throws Exception {
    String documentId = "doc-002";
    when(invoiceRepository.findBySourceInvoiceId(documentId)).thenReturn(Optional.empty());
    when(parserPort.parse(any(), any()))
        .thenThrow(new InvoiceParserPort.InvoiceParsingException("bad xml"));

    service.process(documentId, "<bad/>", "saga-002", SagaStep.PROCESS_INVOICE, "corr-002");

    verify(sagaReplyPort).publishFailure(eq("saga-002"), eq(SagaStep.PROCESS_INVOICE),
        eq("corr-002"), contains("bad xml"));
    verify(eventPublishingPort, never()).publish(any());
}

@Test
void process_shouldBeIdempotent_whenAlreadyProcessed() throws Exception {
    String documentId = "doc-003";
    ProcessedInvoice existing = buildCompletedInvoice(documentId);
    when(invoiceRepository.findBySourceInvoiceId(documentId)).thenReturn(Optional.of(existing));

    service.process(documentId, "<xml/>", "saga-003", SagaStep.PROCESS_INVOICE, "corr-003");

    verify(parserPort, never()).parse(any(), any());
    verify(sagaReplyPort).publishSuccess("saga-003", SagaStep.PROCESS_INVOICE, "corr-003");
}

@Test
void process_shouldPublishSuccessReply_onDataIntegrityViolation() throws Exception {
    String documentId = "doc-004";
    when(invoiceRepository.findBySourceInvoiceId(documentId)).thenReturn(Optional.empty());
    ProcessedInvoice invoice = buildCompletableInvoice(documentId);
    when(parserPort.parse(any(), any())).thenReturn(invoice);
    when(invoiceRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    service.process(documentId, "<xml/>", "saga-004", SagaStep.PROCESS_INVOICE, "corr-004");

    verify(sagaReplyPort).publishSuccess("saga-004", SagaStep.PROCESS_INVOICE, "corr-004");
    verify(eventPublishingPort, never()).publish(any());
}

@Test
void compensate_shouldDeleteInvoiceAndPublishCompensated() {
    String documentId = "doc-005";
    ProcessedInvoice existing = buildCompletedInvoice(documentId);
    when(invoiceRepository.findBySourceInvoiceId(documentId)).thenReturn(Optional.of(existing));

    service.compensate(documentId, "saga-005", SagaStep.PROCESS_INVOICE, "corr-005");

    verify(invoiceRepository).deleteById(existing.getId());
    verify(sagaReplyPort).publishCompensated("saga-005", SagaStep.PROCESS_INVOICE, "corr-005");
}

@Test
void compensate_shouldBeIdempotent_whenInvoiceNotFound() {
    String documentId = "doc-006";
    when(invoiceRepository.findBySourceInvoiceId(documentId)).thenReturn(Optional.empty());

    service.compensate(documentId, "saga-006", SagaStep.PROCESS_INVOICE, "corr-006");

    verify(invoiceRepository, never()).deleteById(any());
    verify(sagaReplyPort).publishCompensated("saga-006", SagaStep.PROCESS_INVOICE, "corr-006");
}
```

**Step 2: Run to confirm failure**

```bash
mvn test -Dtest=InvoiceProcessingServiceTest -q 2>&1 | tail -20
```

Expected: FAIL — service doesn't implement the interfaces yet.

**Step 3: Replace `InvoiceProcessingService`**

```java
package com.wpanther.invoice.processing.application.service;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.out.InvoiceEventPublishingPort;
import com.wpanther.invoice.processing.application.port.out.SagaReplyPort;
import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.ProcessingStatus;
import com.wpanther.invoice.processing.domain.port.out.InvoiceParserPort;
import com.wpanther.invoice.processing.domain.port.out.ProcessedInvoiceRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service implementing both use case inbound ports.
 * Coordinates domain logic and all four outbound ports.
 * Zero imports from infrastructure — dependency rule enforced by package structure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceProcessingService
        implements ProcessInvoiceUseCase, CompensateInvoiceUseCase {

    private final ProcessedInvoiceRepository invoiceRepository;
    private final InvoiceParserPort parserPort;
    private final SagaReplyPort sagaReplyPort;
    private final InvoiceEventPublishingPort eventPublishingPort;

    @Override
    @Transactional
    public void process(String documentId, String xmlContent,
                        String sagaId, SagaStep sagaStep, String correlationId) {
        log.info("Processing invoice for saga={} document={}", sagaId, documentId);
        try {
            if (invoiceRepository.findBySourceInvoiceId(documentId).isPresent()) {
                log.warn("Invoice already processed for document={}, replying SUCCESS", documentId);
                sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                return;
            }

            ProcessedInvoice invoice = parserPort.parse(xmlContent, documentId);

            invoice.startProcessing();
            invoiceRepository.save(invoice);

            invoice.markCompleted(correlationId);
            invoiceRepository.save(invoice);

            invoice.domainEvents().forEach(e -> {
                if (e instanceof InvoiceProcessedDomainEvent domainEvent) {
                    eventPublishingPort.publish(domainEvent);
                }
            });
            invoice.clearDomainEvents();

            sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
            log.info("Successfully processed invoice={} for saga={}", invoice.getInvoiceNumber(), sagaId);

        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted same invoice between check and save.
            // Treat as idempotent success — do not fail the saga.
            log.warn("Duplicate invoice for document={}, treating as idempotent success", documentId);
            sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
        } catch (Exception e) {
            log.error("Failed to process invoice for saga={} document={}: {}",
                sagaId, documentId, e.getMessage(), e);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void compensate(String documentId, String sagaId,
                           SagaStep sagaStep, String correlationId) {
        log.info("Compensating invoice for saga={} document={}", sagaId, documentId);
        invoiceRepository.findBySourceInvoiceId(documentId)
            .ifPresentOrElse(
                invoice -> {
                    invoiceRepository.deleteById(invoice.getId());
                    log.info("Deleted ProcessedInvoice id={} for compensation", invoice.getId());
                },
                () -> log.info("No invoice found for document={} — already compensated or never processed", documentId)
            );
        sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
    }

    @Transactional(readOnly = true)
    public Optional<ProcessedInvoice> findById(String id) {
        try {
            return invoiceRepository.findById(InvoiceId.from(id));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid invoice ID format: {}", id);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<ProcessedInvoice> findByStatus(ProcessingStatus status) {
        return invoiceRepository.findByStatus(status);
    }
}
```

**Step 4: Run tests**

```bash
mvn test -Dtest=InvoiceProcessingServiceTest -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS.

**Step 5: Run full suite**

```bash
mvn test -q 2>&1 | tail -20
```

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/invoice/processing/application/ \
        src/test/java/com/wpanther/invoice/processing/application/
git commit -m "application: refactor InvoiceProcessingService to implement use case ports, drain domain events"
```

---

## STEP 3: Infrastructure Inbound Adapter

> After this step: `SagaCommandHandler` and `InvoiceRouteConfig` live in `infrastructure/adapter/in/messaging/`. Command DTOs are in `dto/`. Old `application/service/SagaCommandHandler` and `infrastructure/config/` are deleted.

---

### Task 11: Create command DTOs in `infrastructure/adapter/in/messaging/dto/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/infrastructure/adapter/in/messaging/dto/ProcessInvoiceCommand.java`
- Create: `src/main/java/com/wpanther/invoice/processing/infrastructure/adapter/in/messaging/dto/CompensateInvoiceCommand.java`
- Delete later: old `domain/event/ProcessInvoiceCommand.java` and `CompensateInvoiceCommand.java`

**Step 1: Create `ProcessInvoiceCommand` — package change only**

```java
package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto;

// All existing imports and class body unchanged from domain/event/ProcessInvoiceCommand
```

**Step 2: Create `CompensateInvoiceCommand` — package change only**

```java
package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto;

// All existing imports and class body unchanged from domain/event/CompensateInvoiceCommand
```

**Step 3: Compile**

```bash
mvn compile -q 2>&1 | head -20
```

Expected: success (old classes still exist, new ones just added).

---

### Task 12: Create new `SagaCommandHandler` in `infrastructure/adapter/in/messaging/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/infrastructure/adapter/in/messaging/SagaCommandHandler.java`
- Create test: `src/test/java/com/wpanther/invoice/processing/infrastructure/adapter/in/messaging/SagaCommandHandlerTest.java`
- Delete later: `src/main/java/com/wpanther/invoice/processing/application/service/SagaCommandHandler.java`

**Step 1: Write failing test**

```java
package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SagaCommandHandlerTest {

    @Mock ProcessInvoiceUseCase processInvoiceUseCase;
    @Mock CompensateInvoiceUseCase compensateInvoiceUseCase;

    @InjectMocks SagaCommandHandler handler;

    @Test
    void handleProcessCommand_shouldDelegateToUseCase() {
        ProcessInvoiceCommand cmd = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-1", "<xml/>", "INV-001"
        );

        handler.handleProcessCommand(cmd);

        verify(processInvoiceUseCase).process(
            "doc-1", "<xml/>", "saga-1", SagaStep.PROCESS_INVOICE, "corr-1"
        );
    }

    @Test
    void handleCompensation_shouldDelegateToUseCase() {
        CompensateInvoiceCommand cmd = new CompensateInvoiceCommand(
            "saga-2", SagaStep.PROCESS_INVOICE, "corr-2", SagaStep.PROCESS_INVOICE, "doc-2", "INVOICE"
        );

        handler.handleCompensation(cmd);

        verify(compensateInvoiceUseCase).compensate(
            "doc-2", "saga-2", SagaStep.PROCESS_INVOICE, "corr-2"
        );
    }
}
```

**Step 2: Run to confirm failure**

```bash
mvn test -Dtest="com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.SagaCommandHandlerTest" -q 2>&1 | tail -15
```

Expected: FAIL — class doesn't exist.

**Step 3: Create the new `SagaCommandHandler`**

```java
package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Primary (driving) adapter — translates Kafka command DTOs into use case calls.
 * No business logic. Pure translation and delegation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ProcessInvoiceUseCase processInvoiceUseCase;
    private final CompensateInvoiceUseCase compensateInvoiceUseCase;

    public void handleProcessCommand(ProcessInvoiceCommand cmd) {
        log.info("Received ProcessInvoiceCommand saga={} document={}",
            cmd.getSagaId(), cmd.getDocumentId());
        processInvoiceUseCase.process(
            cmd.getDocumentId(),
            cmd.getXmlContent(),
            cmd.getSagaId(),
            cmd.getSagaStep(),
            cmd.getCorrelationId()
        );
    }

    public void handleCompensation(CompensateInvoiceCommand cmd) {
        log.info("Received CompensateInvoiceCommand saga={} document={}",
            cmd.getSagaId(), cmd.getDocumentId());
        compensateInvoiceUseCase.compensate(
            cmd.getDocumentId(),
            cmd.getSagaId(),
            cmd.getSagaStep(),
            cmd.getCorrelationId()
        );
    }
}
```

**Step 4: Run test**

```bash
mvn test -Dtest="com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.SagaCommandHandlerTest" -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

---

### Task 13: Move `InvoiceRouteConfig` to `infrastructure/adapter/in/messaging/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/infrastructure/adapter/in/messaging/InvoiceRouteConfig.java`
- Delete: `src/main/java/com/wpanther/invoice/processing/infrastructure/config/InvoiceRouteConfig.java`
- Delete: `src/main/java/com/wpanther/invoice/processing/application/service/SagaCommandHandler.java`
- Delete: `src/main/java/com/wpanther/invoice/processing/domain/event/ProcessInvoiceCommand.java`
- Delete: `src/main/java/com/wpanther/invoice/processing/domain/event/CompensateInvoiceCommand.java`

**Step 1: Create `InvoiceRouteConfig` in `adapter/in/messaging/`**

Copy the existing class verbatim, updating:
- Package: `infrastructure.adapter.in.messaging`
- Import for `SagaCommandHandler`: `infrastructure.adapter.in.messaging.SagaCommandHandler`
- Import for command DTOs: `infrastructure.adapter.in.messaging.dto.*`

**Step 2: Delete old files**

```bash
rm src/main/java/com/wpanther/invoice/processing/infrastructure/config/InvoiceRouteConfig.java
rm src/main/java/com/wpanther/invoice/processing/application/service/SagaCommandHandler.java
rm src/main/java/com/wpanther/invoice/processing/domain/event/ProcessInvoiceCommand.java
rm src/main/java/com/wpanther/invoice/processing/domain/event/CompensateInvoiceCommand.java
rmdir src/main/java/com/wpanther/invoice/processing/infrastructure/config
```

**Step 3: Compile and run tests**

```bash
mvn test -q 2>&1 | tail -20
```

Fix any import errors. Common issue: `InvoiceRouteConfigTest` imports from the old packages.

**Step 4: Commit**

```bash
git add -A
git commit -m "infrastructure: move inbound adapters to adapter/in/messaging/, slim SagaCommandHandler to use case delegation"
```

---

## STEP 4: Infrastructure Outbound Adapters

> After this step: all driven adapters live under `infrastructure/adapter/out/`. Old `infrastructure/messaging/`, `infrastructure/persistence/`, `infrastructure/service/` packages are deleted.

---

### Task 14: Move messaging adapters to `infrastructure/adapter/out/messaging/`

**Step 1: Move + rename `EventPublisher` → `InvoiceEventPublisher`**

Create `infrastructure/adapter/out/messaging/InvoiceEventPublisher.java`:
- Package: `infrastructure.adapter.out.messaging`
- Class renamed: `InvoiceEventPublisher`
- Implements: `InvoiceEventPublishingPort`
- Import for DTO: `infrastructure.adapter.out.messaging.dto.InvoiceProcessedEvent`
- Method `publish(InvoiceProcessedDomainEvent)` logic unchanged from Task 9 update

**Step 2: Move `SagaReplyPublisher`**

Create `infrastructure/adapter/out/messaging/SagaReplyPublisher.java`:
- Package: `infrastructure.adapter.out.messaging`
- Implements: `application.port.out.SagaReplyPort`
- Import for DTO: `infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent`
- All logic unchanged

**Step 3: Move `HeaderSerializer`**

Create `infrastructure/adapter/out/messaging/HeaderSerializer.java` — package change only.

**Step 4: Move outbound DTOs**

Create `infrastructure/adapter/out/messaging/dto/InvoiceReplyEvent.java` — copy verbatim, change package.
Create `infrastructure/adapter/out/messaging/dto/InvoiceProcessedEvent.java` — copy verbatim, change package.

**Step 5: Delete old messaging package and remaining domain/event DTOs**

```bash
rm src/main/java/com/wpanther/invoice/processing/infrastructure/messaging/EventPublisher.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/messaging/SagaReplyPublisher.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/messaging/HeaderSerializer.java
rm src/main/java/com/wpanther/invoice/processing/domain/event/InvoiceReplyEvent.java
rm src/main/java/com/wpanther/invoice/processing/domain/event/InvoiceProcessedEvent.java
rmdir src/main/java/com/wpanther/invoice/processing/infrastructure/messaging
rmdir src/main/java/com/wpanther/invoice/processing/domain/event 2>/dev/null || true
```

**Step 6: Compile**

```bash
mvn compile -q 2>&1 | head -30
```

---

### Task 15: Move persistence adapters to `infrastructure/adapter/out/persistence/`

**Step 1: Move + rename `ProcessedInvoiceRepositoryImpl` → `ProcessedInvoiceRepositoryAdapter`**

Create `infrastructure/adapter/out/persistence/ProcessedInvoiceRepositoryAdapter.java`:
- Package: `infrastructure.adapter.out.persistence`
- Class renamed: `ProcessedInvoiceRepositoryAdapter`
- Import for `ProcessedInvoiceRepository`: `domain.port.out.ProcessedInvoiceRepository`
- All logic unchanged

**Step 2: Move remaining persistence classes**

Move to `infrastructure/adapter/out/persistence/` (package change only):
- `JpaProcessedInvoiceRepository.java`
- `ProcessedInvoiceEntity.java`
- `InvoicePartyEntity.java`
- `InvoiceLineItemEntity.java`
- `ProcessedInvoiceMapper.java`

**Step 3: Delete old persistence package**

```bash
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/ProcessedInvoiceRepositoryImpl.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/JpaProcessedInvoiceRepository.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/ProcessedInvoiceEntity.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/InvoicePartyEntity.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/InvoiceLineItemEntity.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/ProcessedInvoiceMapper.java
rmdir src/main/java/com/wpanther/invoice/processing/infrastructure/persistence
```

---

### Task 16: Move parser adapter to `infrastructure/adapter/out/parsing/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/processing/infrastructure/adapter/out/parsing/InvoiceParserAdapter.java`
- Delete: `src/main/java/com/wpanther/invoice/processing/infrastructure/service/InvoiceParserServiceImpl.java`

**Step 1: Create `InvoiceParserAdapter`**

Copy `InvoiceParserServiceImpl` verbatim with:
- Package: `infrastructure.adapter.out.parsing`
- Class renamed: `InvoiceParserAdapter`
- Implements: `InvoiceParserPort` (already updated in Task 5)
- Method: `parse(...)` (already renamed in Task 5)
- All JAXB parsing logic unchanged

**Step 2: Delete old service**

```bash
rm src/main/java/com/wpanther/invoice/processing/infrastructure/service/InvoiceParserServiceImpl.java
rmdir src/main/java/com/wpanther/invoice/processing/infrastructure/service
```

---

### Task 17: Move outbox classes to `infrastructure/adapter/out/outbox/`

Move to `infrastructure/adapter/out/outbox/` (package change only):
- `SpringDataOutboxRepository.java`
- `JpaOutboxEventRepository.java`
- `OutboxEventEntity.java`

Then delete:

```bash
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/outbox/SpringDataOutboxRepository.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/outbox/JpaOutboxEventRepository.java
rm src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/outbox/OutboxEventEntity.java
rmdir src/main/java/com/wpanther/invoice/processing/infrastructure/persistence/outbox 2>/dev/null || true
```

**Run full test suite after Tasks 14–17:**

```bash
mvn test -q 2>&1 | tail -30
```

Expected: BUILD SUCCESS.

**Commit:**

```bash
git add -A
git commit -m "infrastructure: move all outbound adapters to adapter/out/, rename RepositoryImpl→RepositoryAdapter, EventPublisher→InvoiceEventPublisher, ParserServiceImpl→InvoiceParserAdapter"
```

---

## STEP 5: Test Updates

> After this step: all tests compile against new package paths. `mvn verify` passes with 100% JaCoCo coverage.

---

### Task 18: Update all test file imports

**Step 1: Find all broken imports**

```bash
mvn test-compile 2>&1 | grep "cannot find symbol\|package.*does not exist" | sort -u
```

For each error, update the import in the corresponding test file.

**Common import replacements:**

| Old import | New import |
|---|---|
| `domain.repository.ProcessedInvoiceRepository` | `domain.port.out.ProcessedInvoiceRepository` |
| `domain.service.InvoiceParserService` | `domain.port.out.InvoiceParserPort` |
| `domain.port.SagaReplyPort` | `application.port.out.SagaReplyPort` |
| `domain.event.ProcessInvoiceCommand` | `infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand` |
| `domain.event.CompensateInvoiceCommand` | `infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand` |
| `domain.event.InvoiceReplyEvent` | `infrastructure.adapter.out.messaging.dto.InvoiceReplyEvent` |
| `domain.event.InvoiceProcessedEvent` | `infrastructure.adapter.out.messaging.dto.InvoiceProcessedEvent` |
| `application.service.SagaCommandHandler` | `infrastructure.adapter.in.messaging.SagaCommandHandler` |
| `infrastructure.config.InvoiceRouteConfig` | `infrastructure.adapter.in.messaging.InvoiceRouteConfig` |
| `infrastructure.messaging.EventPublisher` | `infrastructure.adapter.out.messaging.InvoiceEventPublisher` |
| `infrastructure.messaging.SagaReplyPublisher` | `infrastructure.adapter.out.messaging.SagaReplyPublisher` |
| `infrastructure.messaging.HeaderSerializer` | `infrastructure.adapter.out.messaging.HeaderSerializer` |
| `infrastructure.persistence.*` | `infrastructure.adapter.out.persistence.*` |
| `infrastructure.persistence.outbox.*` | `infrastructure.adapter.out.outbox.*` |
| `infrastructure.service.InvoiceParserServiceImpl` | `infrastructure.adapter.out.parsing.InvoiceParserAdapter` |

**Test class moves:**

| Old test class location | New location |
|---|---|
| `application/service/SagaCommandHandlerTest` | `infrastructure/adapter/in/messaging/` |
| `infrastructure/config/InvoiceRouteConfigTest` | `infrastructure/adapter/in/messaging/` |
| `infrastructure/messaging/EventPublisherTest` | `infrastructure/adapter/out/messaging/` (rename → `InvoiceEventPublisherTest`) |
| `infrastructure/messaging/SagaReplyPublisherTest` | `infrastructure/adapter/out/messaging/` |
| `infrastructure/messaging/HeaderSerializerTest` | `infrastructure/adapter/out/messaging/` |
| `infrastructure/persistence/*Test` | `infrastructure/adapter/out/persistence/` |
| `infrastructure/persistence/outbox/*Test` | `infrastructure/adapter/out/outbox/` |
| `infrastructure/service/InvoiceParserServiceImplTest` | `infrastructure/adapter/out/parsing/InvoiceParserAdapterTest` |

**Step 2: Compile and test loop until clean**

```bash
mvn test-compile 2>&1 | grep "error" | head -20
# Fix errors, repeat until:
mvn test -q 2>&1 | tail -10
# Expected: BUILD SUCCESS
```

---

### Task 19: Final verification — `mvn verify` with JaCoCo

**Step 1: Run full verify with coverage**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-processing-service
mvn verify -q 2>&1 | tail -30
```

Expected: BUILD SUCCESS with all JaCoCo 100% coverage checks passing.

**Step 2: If coverage fails, identify uncovered lines**

New packages needing coverage:
- `application/port/in/` — interfaces; covered by `InvoiceProcessingServiceTest`
- `application/port/out/InvoiceEventPublishingPort` — covered by `InvoiceEventPublishingPortTest`
- `domain/event/InvoiceProcessedDomainEvent` — covered by `InvoiceProcessedDomainEventTest`
- `infrastructure/adapter/in/messaging/` — covered by `SagaCommandHandlerTest` and `InvoiceRouteConfigTest`
- `infrastructure/adapter/out/parsing/` — covered by `InvoiceParserAdapterTest`
- `infrastructure/adapter/out/messaging/` — covered by `SagaReplyPublisherTest` and `InvoiceEventPublisherTest`
- `infrastructure/adapter/out/persistence/` — covered by repository and entity tests
- `infrastructure/adapter/out/outbox/` — covered by outbox tests

**Step 3: Final commit**

```bash
git add -A
git commit -m "test: update all test imports and class locations for hexagonal architecture migration"
```

---

## Final Checklist

Run after all tasks complete:

```bash
# 1. No old packages remain
find src/main -path "*/domain/repository*" -o -path "*/domain/service*" -o \
     -path "*/infrastructure/config*" -o -path "*/infrastructure/messaging*" -o \
     -path "*/infrastructure/service*" 2>/dev/null
# Expected: no output

# 2. No infrastructure imports in domain or application service
grep -r "infrastructure" src/main/java/com/wpanther/invoice/processing/domain/ 2>/dev/null
grep -r "infrastructure" src/main/java/com/wpanther/invoice/processing/application/service/ 2>/dev/null
# Expected: no output

# 3. No Kafka/Jackson imports in domain/event
grep -r "kafka\|Jackson\|JsonProperty" src/main/java/com/wpanther/invoice/processing/domain/ 2>/dev/null
# Expected: no output

# 4. Full build and coverage pass
mvn verify -q 2>&1 | tail -5
# Expected: BUILD SUCCESS
```
