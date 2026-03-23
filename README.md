# Invoice Processing Service

Microservice for processing and enriching Thai e-Tax invoice data as a participant in the Saga Orchestration pipeline.

## Overview

The Invoice Processing Service is responsible for:

- **Receiving** `ProcessInvoiceCommand` from the Orchestrator Service via Kafka
- **Parsing** XML invoices using the teda library v1.0.0
- **Calculating** totals, taxes, and other derived values
- **Persisting** processed invoice data to PostgreSQL
- **Logging** compensation events to a permanent audit trail
- **Replying** to the Orchestrator with success or failure via the Transactional Outbox pattern

## Architecture

### Domain-Driven Design (Hexagonal)

```
domain/
├── model/        # ProcessedInvoice (aggregate root), value objects, CompensationLogEntry
├── port/
│   └── out/      # Repository and publisher interfaces (ProcessedInvoiceRepository,
│                 #   CompensationLogRepository, InvoiceParserPort)
└── event/        # Kafka DTOs

application/
└── service/      # InvoiceProcessingService, SagaCommandHandler

infrastructure/
├── adapter/out/persistence/  # JPA entities, Spring Data repositories, mappers
├── messaging/                # EventPublisher, SagaReplyPublisher
├── service/                  # InvoiceParserServiceImpl (teda XML parsing)
└── config/                   # Camel routes, OutboxConfig
```

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Message Routing | Apache Camel 4.14.4 |
| Database | PostgreSQL (`process_db`) |
| Messaging | Apache Kafka |
| Service Discovery | Netflix Eureka |
| Database Migration | Flyway |
| XML Parsing | teda Library v1.0.0 |
| Metrics | Micrometer / Prometheus |

### Saga Orchestration

This service is a **Saga participant**. It does not initiate sagas — it responds to commands from the Orchestrator Service.

```
[Orchestrator] → saga.command.invoice → [InvoiceRouteConfig]
                                              ↓
                                      SagaCommandHandler
                                              ↓
                                      InvoiceProcessingService
                                      (parse → save → outbox)
                                              ↓
                                      outbox_events → Debezium CDC
                                              ↓
                                      saga.reply.invoice → [Orchestrator]
```

**Compensation** (`saga.compensation.invoice`): Hard-deletes the `ProcessedInvoice` row by `documentId` and writes a `CompensationLogEntry` atomically, then replies COMPENSATED.

## Database Schema

Four tables managed by Flyway:

| Table | Purpose |
|-------|---------|
| `processed_invoices` | Main invoice data (aggregate root) |
| `invoice_parties` | Seller and buyer information |
| `invoice_line_items` | Line items with quantities and prices |
| `outbox_events` | Transactional outbox for Debezium CDC |
| `compensation_log` | Permanent audit trail of saga compensation events (never deleted) |

## Kafka Topics

### Consumed

| Topic | Command Class | Handler |
|-------|--------------|---------|
| `saga.command.invoice` | `ProcessInvoiceCommand` | `SagaCommandHandler.handleProcessCommand()` |
| `saga.compensation.invoice` | `CompensateInvoiceCommand` | `SagaCommandHandler.handleCompensation()` |

Both routes use `groupId=invoice-processing-service`, 3 consumers, manual acknowledgment.

### Published (via Outbox)

| Topic | Event Class | Trigger |
|-------|------------|---------|
| `saga.reply.invoice` | `InvoiceReplyEvent` | After every process/compensate call |
| `invoice.processed` | `InvoiceProcessedEvent` | After successful processing (notification) |

Dead letter: `invoice.processing.dlq`

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `process_db` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka server URL |

Service runs on port **8082**.

## Running the Service

### Prerequisites

1. PostgreSQL running with `process_db` database
2. Kafka broker running
3. Eureka server running (optional)
4. teda library installed: `cd ../../../teda && mvn clean install`
5. saga-commons library installed: `cd ../../../saga-commons && mvn clean install`

### Build

```bash
mvn clean package
```

### Run Locally

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=process_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export KAFKA_BROKERS=localhost:9092

mvn spring-boot:run
```

### Run with Docker

```bash
docker build -t invoice-processing-service:latest .

docker run -p 8082:8082 \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=process_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e KAFKA_BROKERS=kafka:29092 \
  invoice-processing-service:latest
```

## API Endpoints

This service is **event-driven only** — no REST endpoints for business operations.

### Actuator

```
GET http://localhost:8082/actuator/health
GET http://localhost:8082/actuator/prometheus
GET http://localhost:8082/actuator/camelroutes
```

## Testing

```bash
# Unit tests
mvn test

# Unit tests + JaCoCo coverage check (85% line coverage per package required)
mvn verify

# Single test
mvn test -Dtest=InvoiceProcessingServiceTest

# Integration tests (requires Docker/Podman)
mvn verify -P integration
```

### Database Migrations

```bash
mvn flyway:migrate
mvn flyway:info
```

## Monitoring

### Custom Metrics (Prometheus)

| Metric | Description |
|--------|-------------|
| `invoice.processing.success` | Successfully processed invoices |
| `invoice.processing.failure` | Failed processing attempts |
| `invoice.processing.idempotent` | Duplicate commands handled idempotently |
| `invoice.processing.race_condition_resolved` | Concurrent-insert races resolved as idempotent |
| `invoice.processing.duration` | Processing time histogram |
| `invoice.compensation.success` | Successful compensations |
| `invoice.compensation.idempotent` | Compensation commands for already-absent invoices |
| `invoice.compensation.failure` | Failed compensation attempts |

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
