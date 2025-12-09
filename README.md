# Invoice Processing Service

Microservice for processing and enriching invoice data in the Invoice Processing System.

## Overview

The Invoice Processing Service is responsible for:

- **Receiving** validated invoices from the Invoice Intake Service
- **Parsing** XML invoices using the teda library
- **Enriching** invoice data with business logic
- **Calculating** totals, taxes, and other derived values
- **Publishing** processed invoice events
- **Requesting** PDF generation

## Architecture

### Domain-Driven Design

This service follows DDD principles with:

- **Aggregates**: `ProcessedInvoice` (root)
- **Value Objects**: `Money`, `Address`, `Party`, `LineItem`, `TaxIdentifier`
- **Domain Services**: `InvoiceParserService`
- **Repositories**: `ProcessedInvoiceRepository`

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL |
| Messaging | Apache Kafka |
| Service Discovery | Netflix Eureka |
| Database Migration | Flyway |

## Database Schema

### Tables

1. **processed_invoices** - Main invoice data
2. **invoice_parties** - Seller and buyer information
3. **invoice_line_items** - Invoice line items with quantities and prices

## Kafka Integration

### Consumed Events

| Event | Topic | Description |
|-------|-------|-------------|
| `InvoiceReceivedEvent` | `invoice.received` | Invoice validated by intake service |

### Published Events

| Event | Topic | Description |
|-------|-------|-------------|
| `InvoiceProcessedEvent` | `invoice.processed` | Invoice processing completed |
| `PdfGenerationRequestedEvent` | `pdf.generation.requested` | Request PDF generation |

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `process_db` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `KAFKA_BROKERS` | Kafka bootstrap servers | `localhost:9092` |
| `EUREKA_URL` | Eureka server URL | `http://localhost:8761/eureka/` |

## Running the Service

### Prerequisites

1. PostgreSQL database running
2. Kafka broker running
3. Eureka server running (optional)
4. Thai e-Tax Invoice library (teda) installed locally

### Build

```bash
mvn clean package
```

### Run Locally

```bash
# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=process_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export KAFKA_BROKERS=localhost:9092

# Run application
mvn spring-boot:run
```

### Run with Docker

```bash
# Build image
docker build -t invoice-processing-service:latest .

# Run container
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

### Health Check

```bash
GET http://localhost:8082/actuator/health
```

### Metrics

```bash
GET http://localhost:8082/actuator/metrics
GET http://localhost:8082/actuator/prometheus
```

## Development

### Project Structure

```
src/main/java/com/invoice/processing/
├── InvoiceProcessingServiceApplication.java
├── domain/
│   ├── model/              # Domain models (aggregates, value objects)
│   ├── repository/         # Repository interfaces
│   ├── service/            # Domain services
│   └── event/              # Integration events
├── application/
│   └── service/            # Application services
└── infrastructure/
    ├── persistence/        # JPA entities, repositories
    ├── messaging/          # Kafka consumers, publishers
    └── config/             # Configuration classes
```

### Building

```bash
# Clean and build
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Run tests only
mvn test
```

### Database Migrations

Flyway migrations are located in `src/main/resources/db/migration/`.

```bash
# Run migrations
mvn flyway:migrate

# Check migration status
mvn flyway:info
```

## Integration with teda Library

This service uses the Thai e-Tax Invoice library (teda) for:

- XML parsing and validation
- JAXB class generation
- Database-backed code lists

**Note**: The `InvoiceParserService` implementation needs to be completed with actual teda library integration.

## Monitoring

### Metrics

The service exposes Prometheus metrics at `/actuator/prometheus`:

- `invoice_processing_total` - Total invoices processed
- `invoice_processing_duration_seconds` - Processing time
- Custom business metrics

### Logging

Structured JSON logging is configured for:

- Application events
- Kafka message processing
- Database operations
- Error tracking

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
