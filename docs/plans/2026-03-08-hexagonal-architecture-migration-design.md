# Hexagonal Architecture Migration Design
**invoice-processing-service**
Date: 2026-03-08

## Context

The `invoice-processing-service` currently follows a DDD layered structure (domain → application → infrastructure) and is approximately 60% hexagonal. This document defines the target "textbook" Hexagonal Architecture (Ports and Adapters) and the layer-by-layer migration plan to achieve it.

This design mirrors the pattern established for `taxinvoice-processing-service` — all five architectural decisions carry over exactly. The differences are naming only (Invoice vs TaxInvoice).

## Goals

- Full restructure into strict Hexagonal Architecture with explicit `port/in/` and `port/out/` directories
- Domain layer: **only outbound ports** (business truth — repository, parser)
- Application layer: **both inbound ports** (use case interfaces) **and outbound ports** (orchestration infrastructure — event publishing, saga replies)
- Infrastructure: explicit `adapter/in/` (driving) and `adapter/out/` (driven) separation
- Introduce proper domain events raised by the aggregate
- Each migration step leaves the service compilable with tests green

## Decisions Made

| Question | Decision |
|---|---|
| Migration scope | Full restructure |
| Port placement | Application layer owns in/out; Domain layer owns out only |
| SagaCommandHandler | Moves to `infrastructure/adapter/in/messaging/` — it is a driving adapter |
| Kafka DTOs | Split by direction: commands → `adapter/in/dto/`, replies → `adapter/out/dto/` |
| Domain events | Introduced — `ProcessedInvoice` raises `InvoiceProcessedDomainEvent` |
| Migration strategy | Layer-by-layer incremental |

---

## Target Package Structure

```
com.wpanther.invoice.processing/
│
├── domain/
│   ├── model/
│   │   ├── ProcessedInvoice.java           ← adds domainEvents + markCompleted(correlationId)
│   │   ├── ProcessingStatus.java
│   │   ├── InvoiceId.java
│   │   ├── Money.java
│   │   ├── LineItem.java
│   │   ├── Party.java
│   │   ├── Address.java
│   │   └── TaxIdentifier.java
│   ├── event/                              ← NEW: pure domain event, zero framework deps
│   │   └── InvoiceProcessedDomainEvent.java
│   └── port/
│       └── out/                            ← REPLACES domain/repository/ + domain/service/ + domain/port/
│           ├── ProcessedInvoiceRepository.java     (moved from domain/repository/)
│           └── InvoiceParserPort.java              (renamed from domain/service/InvoiceParserService)
│
├── application/
│   ├── port/
│   │   ├── in/                             ← NEW: driving/inbound use case interfaces
│   │   │   ├── ProcessInvoiceUseCase.java
│   │   │   └── CompensateInvoiceUseCase.java
│   │   └── out/                            ← REPLACES domain/port/SagaReplyPort + no-interface EventPublisher
│   │       ├── SagaReplyPort.java
│   │       └── InvoiceEventPublishingPort.java
│   └── service/
│       └── InvoiceProcessingService.java   ← implements both use case interfaces
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   └── messaging/                  ← PRIMARY ADAPTERS (driving)
    │   │       ├── InvoiceRouteConfig.java
    │   │       ├── SagaCommandHandler.java
    │   │       └── dto/
    │   │           ├── ProcessInvoiceCommand.java
    │   │           └── CompensateInvoiceCommand.java
    │   └── out/
    │       ├── messaging/                  ← SECONDARY ADAPTERS (driven - event publishing)
    │       │   ├── SagaReplyPublisher.java
    │       │   ├── InvoiceEventPublisher.java
    │       │   ├── HeaderSerializer.java
    │       │   └── dto/
    │       │       ├── InvoiceReplyEvent.java
    │       │       └── InvoiceProcessedEvent.java
    │       ├── persistence/                ← SECONDARY ADAPTERS (driven - storage)
    │       │   ├── ProcessedInvoiceRepositoryAdapter.java
    │       │   ├── JpaProcessedInvoiceRepository.java
    │       │   ├── ProcessedInvoiceEntity.java
    │       │   ├── InvoicePartyEntity.java
    │       │   ├── InvoiceLineItemEntity.java
    │       │   └── ProcessedInvoiceMapper.java
    │       ├── outbox/                     ← SECONDARY ADAPTERS (driven - CDC outbox)
    │       │   ├── SpringDataOutboxRepository.java
    │       │   ├── JpaOutboxEventRepository.java
    │       │   └── OutboxEventEntity.java
    │       └── parsing/                    ← SECONDARY ADAPTERS (driven - XML parsing)
    │           └── InvoiceParserAdapter.java
    └── config/                             ← deleted after migration
```

---

## File Move Reference

| Old path | New path | Change |
|---|---|---|
| `domain/repository/ProcessedInvoiceRepository` | `domain/port/out/` | Move |
| `domain/service/InvoiceParserService` | `domain/port/out/InvoiceParserPort` | Move + rename |
| `domain/port/SagaReplyPort` | `application/port/out/` | Move |
| `domain/event/ProcessInvoiceCommand` | `infrastructure/adapter/in/messaging/dto/` | Move |
| `domain/event/CompensateInvoiceCommand` | `infrastructure/adapter/in/messaging/dto/` | Move |
| `domain/event/InvoiceReplyEvent` | `infrastructure/adapter/out/messaging/dto/` | Move |
| `domain/event/InvoiceProcessedEvent` | `infrastructure/adapter/out/messaging/dto/` | Move |
| `application/service/SagaCommandHandler` | `infrastructure/adapter/in/messaging/` | Move + slim |
| `infrastructure/config/InvoiceRouteConfig` | `infrastructure/adapter/in/messaging/` | Move |
| `infrastructure/messaging/EventPublisher` | `infrastructure/adapter/out/messaging/InvoiceEventPublisher` | Move + rename |
| `infrastructure/messaging/SagaReplyPublisher` | `infrastructure/adapter/out/messaging/` | Move |
| `infrastructure/messaging/HeaderSerializer` | `infrastructure/adapter/out/messaging/` | Move |
| `infrastructure/persistence/ProcessedInvoiceRepositoryImpl` | `infrastructure/adapter/out/persistence/ProcessedInvoiceRepositoryAdapter` | Move + rename |
| `infrastructure/persistence/*.java` | `infrastructure/adapter/out/persistence/` | Move |
| `infrastructure/persistence/outbox/*.java` | `infrastructure/adapter/out/outbox/` | Move |
| `infrastructure/service/InvoiceParserServiceImpl` | `infrastructure/adapter/out/parsing/InvoiceParserAdapter` | Move + rename |

**New files:**
- `domain/event/InvoiceProcessedDomainEvent.java`
- `application/port/in/ProcessInvoiceUseCase.java`
- `application/port/in/CompensateInvoiceUseCase.java`
- `application/port/out/InvoiceEventPublishingPort.java`

---

## Section 1: Domain Layer

### 1.1 Consolidate outbound ports into `domain/port/out/`

`ProcessedInvoiceRepository` moves package-only. `InvoiceParserService` renamed to `InvoiceParserPort`:

```java
// domain/port/out/InvoiceParserPort.java
public interface InvoiceParserPort {
    ProcessedInvoice parse(String xmlContent, String sourceInvoiceId)
        throws InvoiceParsingException;

    class InvoiceParsingException extends Exception {
        public InvoiceParsingException(String message) { super(message); }
        public InvoiceParsingException(String message, Throwable cause) { super(message, cause); }
    }
}
```

Delete: `domain/repository/`, `domain/service/`, `domain/port/` packages.

### 1.2 New domain event

```java
// domain/event/InvoiceProcessedDomainEvent.java
public record InvoiceProcessedDomainEvent(
    InvoiceId invoiceId,
    String invoiceNumber,
    Money total,
    String correlationId,
    Instant occurredAt
) {}
```

### 1.3 Aggregate raises domain event

```java
// Additions to ProcessedInvoice
private final List<Object> domainEvents = new ArrayList<>();

public List<Object> domainEvents() {
    return Collections.unmodifiableList(domainEvents);
}

public void clearDomainEvents() {
    domainEvents.clear();
}

public void markCompleted(String correlationId) {
    if (status != ProcessingStatus.PROCESSING) {
        throw new IllegalStateException("Can only complete from PROCESSING status");
    }
    this.status = ProcessingStatus.COMPLETED;
    this.completedAt = LocalDateTime.now();
    domainEvents.add(new InvoiceProcessedDomainEvent(
        this.id, this.invoiceNumber, this.getTotal(), correlationId, Instant.now()
    ));
}
```

---

## Section 2: Application Layer

### 2.1 Inbound ports — `application/port/in/`

```java
// application/port/in/ProcessInvoiceUseCase.java
public interface ProcessInvoiceUseCase {
    void process(String documentId, String xmlContent,
                 String sagaId, SagaStep sagaStep, String correlationId);
}

// application/port/in/CompensateInvoiceUseCase.java
public interface CompensateInvoiceUseCase {
    void compensate(String documentId, String sagaId,
                    SagaStep sagaStep, String correlationId);
}
```

### 2.2 Outbound ports — `application/port/out/`

```java
// application/port/out/SagaReplyPort.java
public interface SagaReplyPort {
    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId);
    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage);
    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}

// application/port/out/InvoiceEventPublishingPort.java
public interface InvoiceEventPublishingPort {
    void publish(InvoiceProcessedDomainEvent event);
}
```

### 2.3 Use case implementation

`InvoiceProcessingService` implements both use case interfaces. Key difference from taxinvoice-processing-service: preserves `DataIntegrityViolationException` handler for race condition duplicate detection — treated as idempotent SUCCESS:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceProcessingService
        implements ProcessInvoiceUseCase, CompensateInvoiceUseCase {

    private final ProcessedInvoiceRepository invoiceRepository;   // domain/port/out
    private final InvoiceParserPort parserPort;                   // domain/port/out
    private final SagaReplyPort sagaReplyPort;                   // application/port/out
    private final InvoiceEventPublishingPort eventPublishingPort; // application/port/out

    @Override
    @Transactional
    public void process(String documentId, String xmlContent,
                        String sagaId, SagaStep sagaStep, String correlationId) {
        try {
            if (invoiceRepository.findBySourceInvoiceId(documentId).isPresent()) {
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
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate invoice for document={}, treating as idempotent success", documentId);
            sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
        } catch (Exception e) {
            log.error("Failed to process invoice document={}: {}", documentId, e.getMessage(), e);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void compensate(String documentId, String sagaId,
                           SagaStep sagaStep, String correlationId) {
        invoiceRepository.findBySourceInvoiceId(documentId)
            .ifPresentOrElse(
                invoice -> {
                    invoiceRepository.deleteById(invoice.getId());
                    log.info("Deleted ProcessedInvoice id={} for compensation", invoice.getId());
                },
                () -> log.info("No invoice found for document={} — already compensated", documentId)
            );
        sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
    }
}
```

**Dependency rule:** Zero imports from `infrastructure/`.

---

## Section 3: Infrastructure Layer

### 3.1 Primary adapter — `infrastructure/adapter/in/messaging/`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {
    private final ProcessInvoiceUseCase processInvoiceUseCase;
    private final CompensateInvoiceUseCase compensateInvoiceUseCase;

    public void handleProcessCommand(ProcessInvoiceCommand cmd) {
        processInvoiceUseCase.process(
            cmd.getDocumentId(), cmd.getXmlContent(),
            cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId()
        );
    }

    public void handleCompensation(CompensateInvoiceCommand cmd) {
        compensateInvoiceUseCase.compensate(
            cmd.getDocumentId(), cmd.getSagaId(),
            cmd.getSagaStep(), cmd.getCorrelationId()
        );
    }
}
```

`InvoiceRouteConfig` moves to same package — imports updated for new DTO and handler locations. Route IDs (`saga-command-consumer`, `saga-compensation-consumer`) and all Camel config unchanged.

### 3.2 Secondary adapter — `InvoiceEventPublisher`

Translates `InvoiceProcessedDomainEvent` → Kafka DTO before writing to outbox:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceEventPublisher implements InvoiceEventPublishingPort {

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(InvoiceProcessedDomainEvent domainEvent) {
        InvoiceProcessedEvent kafkaEvent = new InvoiceProcessedEvent(
            domainEvent.invoiceId().value().toString(),
            domainEvent.invoiceNumber(),
            domainEvent.total().amount(),
            domainEvent.total().currency(),
            domainEvent.correlationId()
        );
        outboxService.saveWithRouting(kafkaEvent, "ProcessedInvoice",
            domainEvent.invoiceId().value().toString(),
            "invoice.processed",
            domainEvent.invoiceId().value().toString(),
            headerSerializer.toJson(Map.of(
                "correlationId", domainEvent.correlationId(),
                "invoiceNumber", domainEvent.invoiceNumber()
            ))
        );
    }
}
```

### 3.3 Secondary adapters — persistence, outbox, parsing

- `ProcessedInvoiceRepositoryImpl` → `ProcessedInvoiceRepositoryAdapter` in `adapter/out/persistence/`
- `InvoiceParserServiceImpl` → `InvoiceParserAdapter` implementing `InvoiceParserPort` in `adapter/out/parsing/`
- All outbox classes → `adapter/out/outbox/`

### 3.4 Infrastructure dependency rules

| Adapter | May import from | Must NOT import from |
|---|---|---|
| `adapter/in/messaging/` | `application/port/in/`, adapter `dto/` | `application/service/`, `domain/model/` directly |
| `adapter/out/messaging/` | `application/port/out/`, `domain/event/`, adapter `dto/` | `application/service/` |
| `adapter/out/persistence/` | `domain/port/out/`, `domain/model/` | `application/` |
| `adapter/out/parsing/` | `domain/port/out/`, `domain/model/` | `application/` |

---

## Section 4: Migration Sequence

### Step 1 — Domain layer restructure
1. Create `domain/port/out/ProcessedInvoiceRepository` (package change only)
2. Create `domain/port/out/InvoiceParserPort` (renamed from `InvoiceParserService`)
3. Update `InvoiceParserServiceImpl` — implements `InvoiceParserPort`, rename `parseInvoice` → `parse`
4. Update `ProcessedInvoiceRepositoryImpl` import
5. Update `InvoiceProcessingService` + `SagaCommandHandler` imports
6. Delete `domain/repository/`, `domain/service/`, `domain/port/` packages
7. Create `domain/event/InvoiceProcessedDomainEvent` record
8. Add `domainEvents` list + `markCompleted(correlationId)` + `clearDomainEvents()` to `ProcessedInvoice`
9. ✅ `mvn test` — all tests green

### Step 2 — Application layer restructure
1. Create `application/port/in/ProcessInvoiceUseCase` and `CompensateInvoiceUseCase`
2. Create `application/port/out/SagaReplyPort` (moved from `domain/port/`)
3. Create `application/port/out/InvoiceEventPublishingPort`
4. Refactor `InvoiceProcessingService` — implements both use cases, injects all four ports, drains domain events, preserves `DataIntegrityViolationException` handler
5. Update `SagaReplyPublisher` implements clause → `application/port/out/SagaReplyPort`
6. Update `EventPublisher` implements clause → `InvoiceEventPublishingPort`
7. ✅ `mvn test` — all tests green

### Step 3 — Infrastructure inbound adapter
1. Create `infrastructure/adapter/in/messaging/dto/` — move command DTOs from `domain/event/`
2. Create new `SagaCommandHandler` in `infrastructure/adapter/in/messaging/` — use case interfaces only
3. Move `InvoiceRouteConfig` → `infrastructure/adapter/in/messaging/`, update imports
4. Delete old `SagaCommandHandler` from `application/service/`, old `InvoiceRouteConfig` from `infrastructure/config/`, old command DTOs from `domain/event/`
5. ✅ `mvn test` — all tests green

### Step 4 — Infrastructure outbound adapters
1. Move + rename `EventPublisher` → `InvoiceEventPublisher` in `adapter/out/messaging/`, translate domain event → Kafka DTO
2. Move `SagaReplyPublisher` + `HeaderSerializer` → `adapter/out/messaging/`
3. Move reply/notification DTOs → `adapter/out/messaging/dto/`
4. Move + rename `ProcessedInvoiceRepositoryImpl` → `ProcessedInvoiceRepositoryAdapter` in `adapter/out/persistence/`
5. Move remaining persistence classes → `adapter/out/persistence/`
6. Move + rename `InvoiceParserServiceImpl` → `InvoiceParserAdapter` in `adapter/out/parsing/`
7. Move outbox classes → `adapter/out/outbox/`
8. Delete empty packages: `infrastructure/messaging/`, `infrastructure/persistence/`, `infrastructure/service/`, `infrastructure/config/`, remaining `domain/event/`
9. ✅ `mvn verify` — full coverage check green

### Step 5 — Test updates (parallel with Steps 3–4)

| Test class | Change |
|---|---|
| `SagaCommandHandlerTest` | Move to `adapter/in/messaging/`, inject use case mocks |
| `InvoiceProcessingServiceTest` | Update port mocks, verify domain event draining, verify `DataIntegrityViolationException` → SUCCESS reply |
| `InvoiceParserServiceImplTest` | Rename → `InvoiceParserAdapterTest`, move to `adapter/out/parsing/` |
| `InvoiceRouteConfigTest` | Move to `adapter/in/messaging/`, update imports |
| `EventPublisherTest` | Rename → `InvoiceEventPublisherTest`, test domain event → Kafka DTO translation |
| `SagaReplyPublisherTest` | Move to `adapter/out/messaging/`, update imports |
| All domain model tests | No changes |

---

## Section 5: Testing Strategy

### Domain layer — pure unit tests, zero mocks

```java
@Test
void markCompleted_shouldRaiseInvoiceProcessedDomainEvent() {
    ProcessedInvoice invoice = buildValidInvoice();
    invoice.startProcessing();
    invoice.markCompleted("corr-abc");

    assertThat(invoice.domainEvents()).hasSize(1);
    InvoiceProcessedDomainEvent event =
        (InvoiceProcessedDomainEvent) invoice.domainEvents().get(0);
    assertThat(event.correlationId()).isEqualTo("corr-abc");
}
```

### Application layer — mocked ports, no Spring context

```java
@ExtendWith(MockitoExtension.class)
class InvoiceProcessingServiceTest {
    @Mock ProcessedInvoiceRepository invoiceRepository;
    @Mock InvoiceParserPort parserPort;
    @Mock SagaReplyPort sagaReplyPort;
    @Mock InvoiceEventPublishingPort eventPublishingPort;
    @InjectMocks InvoiceProcessingService service;

    // Tests: process success, idempotency, parsing failure,
    //        DataIntegrityViolationException → SUCCESS reply,
    //        compensation found, compensation not found
}
```

### Infrastructure adapters — each isolated

| Adapter | Test approach |
|---|---|
| `InvoiceParserAdapter` | Unit — real JAXB + XML fixture |
| `ProcessedInvoiceRepositoryAdapter` | `@DataJpaTest` with H2 |
| `InvoiceEventPublisher` | Unit — mock `OutboxService`, verify domain event → DTO translation |
| `SagaReplyPublisher` | Unit — mock `OutboxService` |
| `SagaCommandHandler` | Unit — mock both use case interfaces |
| `InvoiceRouteConfig` | `@CamelSpringBootTest` — verify route IDs |

**Coverage:** JaCoCo 100% line coverage per package maintained throughout migration.
