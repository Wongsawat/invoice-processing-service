# Integration Tests for taxinvoice-processing-service

## Overview

This service has **263 tests** in total:
- **250 unit tests** - Framework-independent tests with mocked dependencies
- **7 Saga consumer integration tests** - End-to-end tests through Apache Camel routes
- **6 CDC (Debezium) integration tests** - Outbox pattern event delivery verification

Integration tests exercise the full saga flow with real infrastructure components (PostgreSQL, Kafka, Debezium) and validate that events are correctly produced and consumed.

---

## Prerequisites

### Required Software
- Java 21+
- Maven 3.6+
- Docker or Podman (for test containers)

### External Dependencies
- **saga-commons library** installed: `cd ../../saga-commons && mvn clean install`

---

## Test Containers

The integration tests use Docker containers for infrastructure:

| Service | Host Port | Purpose |
|---------|----------|---------|
| PostgreSQL | 5433 | Real database with Flyway migrations |
| Kafka | 9093 | Real message broker for saga commands/replies |
| Debezium | 8083 | CDC for outbox table event delivery |
| MongoDB | 27018 | Not used by this service (document-storage-service only) |

### Starting Test Containers

```bash
cd /home/wpanther/projects/etax/invoice-microservices

# Start PostgreSQL + Kafka (for Kafka consumer tests)
./scripts/test-containers-start.sh

# Start PostgreSQL + Kafka + Debezium (for CDC tests)
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors

# Stop containers
./scripts/test-containers-stop.sh
```

The `--auto-deploy-connectors` flag deploys the `taxinvoice-connector.json` Debezium connector that monitors the `outbox_events` table.

---

## Running Integration Tests

### Run Only Unit Tests (Default)

```bash
cd services/taxinvoice-processing-service
mvn test
```

### Run Saga Consumer Integration Tests

```bash
cd services/taxinvoice-processing-service
mvn test -Pintegration -Dtest=KafkaConsumerIntegrationTest
```

### Run CDC Integration Tests

```bash
cd services/taxinvoice-processing-service
mvn test -Pintegration -Dtest=TaxInvoiceCdcIntegrationTest
```

### Run All Integration Tests

```bash
cd services/taxinvoice-processing-service
mvn test -Pintegration
```

### Run Single Test Method

```bash
cd services/taxinvoice-processing-service
mvn test -Pintegration -Dtest=KafkaConsumerIntegrationTest#shouldProcessValidTaxInvoiceEndToEnd
```

---

## Test Architecture

### Saga Consumer Integration Tests

```
KafkaProducer (test) → saga.command.tax-invoice → Camel Consumer Route → SagaCommandHandler
                                                                         ↓
                                                    TaxInvoiceProcessingService
                                                                         ↓
                                             Parse XML → Save to DB → Outbox Table
                                                                         ↓
                                                          saga.reply.tax-invoice (SUCCESS/FAILURE)
                                                                         ↓
                                                    KafkaConsumer (test) verifies reply
```

**Base Class:** `AbstractKafkaConsumerTest`
- `@SpringBootTest` with full application context
- `@ActiveProfiles("consumer-test")` - uses `application-consumer-test.yml`
- Sends events via `KafkaTemplate`
- Polls database for results using `JdbcTemplate`
- Uses unique invoice numbers per test to handle stale Kafka messages

**Configuration:**
- Test profile: `src/test/resources/application-consumer-test.yml`
- Producer config: `TestKafkaProducerConfig.java`
- Test configuration: `ConsumerTestConfiguration.java`

### CDC Integration Tests

```
Service → PostgreSQL → Outbox Table → Debezium CDC → Kafka → KafkaConsumer (test)
```

**Base Class:** `AbstractCdcIntegrationTest`
- `@SpringBootTest(classes = CdcTestConfiguration.class)` - minimal Spring context
- `@ActiveProfiles("cdc-test")` - uses `application-cdc-test.yml`
- Direct service calls (no Camel routes)
- Polls Kafka via `KafkaConsumer` for CDC output
- Verifies connector status before running

**Configuration:**
- Test profile: `src/test/resources/application-cdc-test.yml`
- Test configuration: `CdcTestConfiguration.java`
- Consumer config: `TestKafkaConsumerConfig.java`

---

## Test Descriptions

### Saga Consumer Integration Tests (7 tests)

| Test | Description |
|------|-------------|
| `shouldProcessValidTaxInvoiceEndToEnd` | Full E2E: validates invoice persisted with correct parties, line items, totals, and 2 outbox events (reply + notification) |
| `shouldCreateOutboxEventsForProcessedInvoice` | Verifies outbox events with correct metadata (aggregate_type, partition_key, status, headers) |
| `shouldCalculateTotalsCorrectly` | Validates totals: 60000 subtotal, 4200 tax, 64200 total |
| `shouldSkipDuplicateEvent` | Idempotency: duplicate `documentId` only creates 1 invoice |
| `shouldProcessEventsWithDifferentDocumentIds` | Multiple events processed separately |
| `shouldNotPersistForInvalidXml` | Invalid XML doesn't persist (exception caught silently) |
| `shouldNotPersistForEmptyXml` | Empty XML doesn't persist (exception caught silently) |
| `shouldCompensateProcessedInvoice` | Compensation: deletes invoice and sends COMPENSATED reply |

### CDC Integration Tests (6 tests)

| Test | Description |
|------|-------------|
| `shouldPersistProcessedTaxInvoice` | Service call persists invoice with status COMPLETED |
| `shouldCreateOutboxEntries` | Service creates 2 outbox events with correct metadata |
| `shouldWriteProcessedEventToOutbox` | taxinvoice.processed outbox entry verified |
| `shouldPublishProcessedEventViaCdc` | Debezium publishes taxinvoice.processed to Kafka |
| `shouldPublishSagaReplyViaCdc` | Debezium publishes saga.reply.tax-invoice to Kafka |
| `shouldHandleSagaCommandAndReply` | Full flow: saga command → process → reply → CDC verification |

---

## Key Implementation Details

### Unique Invoice Numbers

All tests generate unique invoice numbers to handle stale Kafka messages from previous test runs:
```java
String invoiceNumber = "TV-" + UUID.randomUUID().toString().substring(0, 8);
```

### Immutable Event Constructors

All events use the 4-arg constructor (final fields, no setters):
```java
new ProcessTaxInvoiceCommand(sagaId, sagaStep, correlationId, documentId, xmlContent, invoiceNumber)
```

### Error Handling Behavior

The `SagaCommandHandler.handleProcessCommand()` method catches all exceptions and sends a FAILURE reply. Errors are logged but not rethrown, so Camel commits the Kafka offset.

### Database Cleanup

Each test deletes tables in foreign-key-safe order:
```java
DELETE FROM outbox_events;
DELETE FROM tax_invoice_line_items;
DELETE FROM tax_invoice_parties;
DELETE FROM processed_tax_invoices;
```

### Debezium Connector Configuration

Located at: `/home/wpanther/projects/etax/invoice-microservices/docker/debezium-connectors/taxinvoice-connector.json`

Monitors:
- Table: `public.outbox_events`
- Routes events by: `topic` field
- Key field: `partition_key`
- Payload field: `payload`

---

## Troubleshooting

### Tests Fail with "PostgreSQL not available"

```bash
# Start test containers
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh
```

### CDC Tests Fail with "Debezium not available"

```bash
# Start Debezium with connectors
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
```

### Tests Fail with "ConditionTimeoutException"

This usually indicates Debezium is not running or connector is not deployed:
- Check connector status: `./scripts/check-connectors.sh`
- Verify connector is RUNNING: `curl http://localhost:8083/connectors/taxinvoice-connector/status`

### Tests Fail with Duplicate Key Violation

This was fixed in `ProcessedTaxInvoiceRepositoryImpl.save()`:
- Ensure fix is present: check for existing entity before remapping
- If issues persist, reset Kafka topics: `docker exec test-kafka kafka-topics --bootstrap-server localhost:9093 --delete --topic saga.command.tax-invoice`

### Podman Instead of Docker

```bash
export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
```

---

## File Structure

```
src/test/
├── resources/
│   ├── application.yml                    # Main config
│   ├── application-test.yml               # Unit test config (H2)
│   ├── application-consumer-test.yml      # Kafka consumer test config (PostgreSQL)
│   └── application-cdc-test.yml           # CDC test config (PostgreSQL, no Camel)
└── java/
    └── .../integration/
        ├── config/
        │   ├── TestKafkaProducerConfig.java      # KafkaTemplate for sending
        │   ├── ConsumerTestConfiguration.java     # JdbcTemplate bean
        │   ├── TestKafkaConsumerConfig.java      # KafkaConsumer for CDC
        │   └── CdcTestConfiguration.java         # Minimal Spring context
        ├── AbstractKafkaConsumerTest.java        # Base class for consumer tests
        ├── AbstractCdcIntegrationTest.java       # Base class for CDC tests
        ├── KafkaConsumerIntegrationTest.java      # 7 consumer tests
        └── TaxInvoiceCdcIntegrationTest.java     # 6 CDC tests
```

---

## Maven Profiles

### Default Profile
- Excludes `**/integration/**` from surefire plugin
- Only runs unit tests

### Integration Profile (`-Pintegration`)
- Sets `integration.tests.enabled=true` system property
- Tests annotated with `@EnabledIfSystemProperty` are executed
- Runs both unit and integration tests

---

## CI/CD Considerations

For CI/CD pipelines:
1. Start test containers in background before tests
2. Run unit tests: `mvn test`
3. Start Debezium: `./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors`
4. Run integration tests: `mvn test -Pintegration`
5. Stop containers: `./scripts/test-containers-stop.sh`

Example CI script:
```bash
#!/bin/bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
cd services/taxinvoice-processing-service
mvn test -Pintegration
cd ../..
./scripts/test-containers-stop.sh
```
