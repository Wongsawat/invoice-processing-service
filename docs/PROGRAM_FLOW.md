# Program Flow

This document describes the complete program flow of the Invoice Processing Service, from application startup through invoice processing.

## 1. Application Startup

```
InvoiceProcessingServiceApplication.main()
           │
           ▼
┌─────────────────────────────────────────┐
│       Spring Boot Initialization        │
│                                         │
│  @SpringBootApplication                 │
│  @EnableKafka                           │
│  @EnableDiscoveryClient (Eureka)        │
│  @EnableTransactionManagement           │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│         Auto-Configuration              │
│                                         │
│  1. DataSource (PostgreSQL via HikariCP)│
│  2. JPA/Hibernate                       │
│  3. Flyway migrations                   │
│  4. Kafka producer/consumer factories   │
│  5. Eureka client registration          │
│  6. Actuator endpoints                  │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│         Kafka Configuration             │
│         (KafkaConfig.java)              │
│                                         │
│  • ProducerFactory (acks=all, retries=3)│
│  • ConsumerFactory (manual commit)      │
│  • KafkaListenerContainerFactory        │
│    - Concurrency: 3 threads             │
│    - AckMode: MANUAL                    │
└─────────────────────────────────────────┘
           │
           ▼
    Application Ready
    (Listening on port 8082)
```

## 2. Kafka Event Consumption

When an `InvoiceReceivedEvent` arrives on the `document.received.invoice` topic:

```
Kafka Topic: document.received.invoice
           │
           ▼
┌─────────────────────────────────────────┐
│       InvoiceEventListener              │
│       (infrastructure/messaging)        │
│                                         │
│  @KafkaListener(                        │
│    topics = "document.received.invoice",│
│    groupId = "invoice-processing-srv"   │
│  )                                      │
│                                         │
│  Receives:                              │
│  • InvoiceReceivedEvent (payload)       │
│  • Partition number                     │
│  • Offset                               │
│  • Acknowledgment handle                │
└─────────────────────────────────────────┘
           │
           ▼
    processingService.processInvoiceReceived(event)
```

## 3. Invoice Processing Flow

```
InvoiceProcessingService.processInvoiceReceived(event)
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 1: Idempotency Check          │
│                                         │
│  invoiceRepository.findBySourceInvoiceId│
│                                         │
│  If already exists → Return early       │
│  (Prevents duplicate processing)        │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 2: Parse XML Invoice          │
│                                         │
│  parserService.parseInvoice(            │
│    xmlContent,                          │
│    documentId                           │
│  )                                      │
│                                         │
│  Returns: ProcessedInvoice domain model │
│  (Uses teda library v1.0.0 -             │
│   Invoice_CrossIndustryInvoice)         │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 3: Start Processing           │
│                                         │
│  invoice.startProcessing()              │
│                                         │
│  Status: PENDING → PROCESSING           │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 4: Persist Invoice            │
│                                         │
│  invoiceRepository.save(invoice)        │
│                                         │
│  Domain Model → JPA Entity → PostgreSQL │
│  (ProcessedInvoiceMapper handles        │
│   bidirectional conversion)             │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 5: Mark Completed             │
│                                         │
│  invoice.markCompleted()                │
│  invoiceRepository.save(invoice)        │
│                                         │
│  Status: PROCESSING → COMPLETED         │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 6: Publish Processed Event    │
│                                         │
│  eventPublisher.publishInvoiceProcessed │
│                                         │
│  InvoiceProcessedEvent contains:        │
│  • invoiceId                            │
│  • invoiceNumber                        │
│  • total amount                         │
│  • currency                             │
│  • correlationId                        │
│                                         │
│  → Kafka Topic: invoice.processed       │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 7: Request XML Signing        │
│                                         │
│  invoice.requestPdfGeneration()         │
│  invoiceRepository.save(invoice)        │
│                                         │
│  Status: COMPLETED → PDF_REQUESTED      │
└─────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│      Step 8: Publish XML Signing Request│
│                                         │
│  eventPublisher.publishXmlSigning       │
│                                         │
│  XmlSigningRequestedEvent contains:     │
│  • invoiceId                            │
│  • invoiceNumber                        │
│  • originalXml                          │
│  • invoiceDataJson                      │
│  • correlationId                        │
│                                         │
│  → Kafka Topic: xml.signing.requested  │
└─────────────────────────────────────────┘
           │
           ▼
    Return to InvoiceEventListener
           │
           ▼
    acknowledgment.acknowledge()
    (Commit Kafka offset)
```

## 4. State Machine

The `ProcessedInvoice` aggregate enforces valid state transitions:

```
                    ┌─────────────────┐
                    │     PENDING     │
                    └────────┬────────┘
                             │ startProcessing()
                             ▼
                    ┌─────────────────┐
       ┌───────────▶│   PROCESSING    │◀───────────┐
       │            └────────┬────────┘            │
       │                     │                     │
       │     markFailed()    │    markCompleted()  │
       │                     │                     │
       ▼                     ▼                     │
┌─────────────┐      ┌─────────────────┐           │
│   FAILED    │      │   COMPLETED     │           │
└─────────────┘      └────────┬────────┘           │
                              │                    │
                              │ requestPdfGeneration()
                              ▼                    │
                     ┌─────────────────┐           │
                     │  PDF_REQUESTED  │───────────┘
                     └────────┬────────┘  (retry on failure)
                              │
                              │ markPdfGenerated()
                              ▼
                     ┌─────────────────┐
                     │  PDF_GENERATED  │
                     └─────────────────┘
```

## 5. Repository Layer Flow

```
Domain Layer                    Infrastructure Layer                Database
─────────────────────────────────────────────────────────────────────────────

ProcessedInvoice          ProcessedInvoiceRepositoryImpl        PostgreSQL
(domain/model)            (infrastructure/persistence)
      │                              │
      │  save(invoice)               │
      ├─────────────────────────────▶│
      │                              │
      │                   ProcessedInvoiceMapper.toEntity()
      │                              │
      │                   ProcessedInvoiceEntity
      │                   InvoicePartyEntity (seller)
      │                   InvoicePartyEntity (buyer)
      │                   InvoiceLineItemEntity (items)
      │                              │
      │                   jpaRepository.save(entity)
      │                              ├───────────────────────▶ processed_invoices
      │                              │                        invoice_parties
      │                              │                        invoice_line_items
      │                              │
      │                   ProcessedInvoiceMapper.toDomain()
      │                              │
      │◀─────────────────────────────┤
ProcessedInvoice (updated)
```

## 6. Error Handling Flow

```
InvoiceEventListener.handleInvoiceReceived()
           │
           ▼
    ┌──────────────────┐
    │  try { ... }     │
    │                  │
    │  processingService.processInvoiceReceived(event)
    │                  │
    └────────┬─────────┘
             │
    ┌────────┴─────────┐
    │                  │
    ▼                  ▼
Success            Exception
    │                  │
    │                  │
    ▼                  ▼
acknowledgment    Log error
.acknowledge()    (No ack)
    │                  │
    │                  │
    ▼                  ▼
Offset committed  Message retained
                  for retry
```

## 7. Kafka Message Format

### InvoiceReceivedEvent (Consumed)

```json
{
  "eventId": "uuid",
  "occurredAt": "2024-01-01T12:00:00Z",
  "eventType": "invoice.received",
  "version": 1,
  "documentId": "source-document-uuid",
  "invoiceNumber": "INV-2024-001",
  "xmlContent": "<Invoice>...</Invoice>",
  "correlationId": "correlation-uuid"
}
```

### InvoiceProcessedEvent (Published)

```json
{
  "eventId": "uuid",
  "occurredAt": "2024-01-01T12:00:01Z",
  "eventType": "invoice.processed",
  "version": 1,
  "invoiceId": "processed-invoice-uuid",
  "invoiceNumber": "INV-2024-001",
  "totalAmount": 1000.00,
  "currency": "THB",
  "correlationId": "correlation-uuid"
}
```

### XmlSigningRequestedEvent (Published)

```json
{
  "eventId": "uuid",
  "occurredAt": "2024-01-01T12:00:02Z",
  "eventType": "xml.signing.requested",
  "version": 1,
  "invoiceId": "processed-invoice-uuid",
  "invoiceNumber": "INV-2024-001",
  "xmlContent": "<Invoice>...</Invoice>",
  "invoiceDataJson": "{\"invoiceNumber\":\"INV-2024-001\",...}",
  "correlationId": "correlation-uuid"
}
```

## 8. Component Dependencies

```
┌──────────────────────────────────────────────────────────────┐
│                    InvoiceEventListener                      │
│                    (Entry Point)                             │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                 InvoiceProcessingService                     │
│                 (Application Layer)                          │
├──────────────────────────────────────────────────────────────┤
│  Dependencies:                                               │
│  • ProcessedInvoiceRepository (domain interface)             │
│  • InvoiceParserService (domain interface)                   │
│  • EventPublisher (infrastructure)                           │
│  • ObjectMapper (Jackson)                                    │
└─────────┬────────────────┬───────────────────┬───────────────┘
          │                │                   │
          ▼                ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ ProcessedInvoice│ │ InvoiceParser   │ │ EventPublisher  │
│ RepositoryImpl  │ │ Service (impl)  │ │                 │
│                 │ │                 │ │                 │
│ Uses:           │ │ Uses:           │ │ Uses:           │
│ • JpaRepository │ │ • teda library  │ │ • KafkaTemplate │
│ • Mapper        │ │   v1.0.0        │ │                 │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

## 9. Transaction Boundaries

```
@Transactional (InvoiceProcessingService.processInvoiceReceived)
│
├── Check idempotency (READ)
├── Parse XML (no DB)
├── Save invoice (WRITE)
├── Update status (WRITE)
├── Publish event (async, non-transactional)
├── Update status (WRITE)
├── Publish event (async, non-transactional)
│
└── COMMIT (or ROLLBACK on exception)

Note: Kafka sends are NOT part of the transaction.
      Events may be published even if transaction rolls back.
      Consider Outbox pattern for exactly-once semantics.
```

## 10. Thread Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Kafka Consumer Threads                    │
│                    (Concurrency = 3)                         │
├─────────────────────────────────────────────────────────────┤
│  Thread-1 ─▶ Partition 0 ─▶ InvoiceEventListener            │
│  Thread-2 ─▶ Partition 1 ─▶ InvoiceEventListener            │
│  Thread-3 ─▶ Partition 2 ─▶ InvoiceEventListener            │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    HikariCP Connection Pool                  │
│                    (max-pool-size = 10)                      │
├─────────────────────────────────────────────────────────────┤
│  Each processing thread acquires connection from pool        │
│  Connection released after transaction commit/rollback       │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Kafka Producer                            │
│                    (Async sends with callbacks)              │
├─────────────────────────────────────────────────────────────┤
│  CompletableFuture<SendResult> for non-blocking sends        │
│  Callbacks log success/failure                               │
└─────────────────────────────────────────────────────────────┘
```

## Version 1.0.0 Updates

### Package Changes
- Application package changed from `com.invoice.processing` to `com.wpanther.invoice.processing`
- Maven groupId changed from `com.invoice` to `com.wpanther`

### Kafka Topic Changes
- Now consumes from `document.received.invoice` (was `invoice.received`)
- Publishes to `xml.signing.requested` (was `pdf.generation.requested`)
- PDF generation is now triggered by XML Signing Service after signing

### Event Field Changes
- `InvoiceReceivedEvent.documentId` (was `invoiceId`)
- Maps to `sourceInvoiceId` in domain model

### teda Library Changes
- Uses teda v1.0.0
- Root element: `Invoice_CrossIndustryInvoice` (was `TaxInvoice_CrossIndustryInvoice`)
- JAXB packages: `com.wpanther.etax.generated.invoice.*` (was `taxinvoice.*`)
