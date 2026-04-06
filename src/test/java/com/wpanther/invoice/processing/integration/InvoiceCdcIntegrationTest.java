package com.wpanther.invoice.processing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@DisplayName("CDC Integration Tests")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class InvoiceCdcIntegrationTest extends AbstractCdcIntegrationTest {

    // ========== Database Write Tests ==========

    @Test
    @DisplayName("Should persist processed invoice to database")
    void shouldPersistProcessedInvoice() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When — call saga handler directly (no Camel consumer in CDC tests)
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        Map<String, Object> invoice = getInvoiceBySourceId(documentId);
        assertThat(invoice).isNotNull();
        assertThat(invoice.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(invoice.get("status")).isEqualTo("COMPLETED");
        assertThat(invoice.get("currency")).isEqualTo("THB");
    }

    @Test
    @DisplayName("Should create outbox event entries with correct metadata")
    void shouldCreateOutboxEntries() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String sagaId = "saga-" + correlationId;

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then — verify invoice.processed outbox event
        getInvoiceBySourceId(documentId);

        List<Map<String, Object>> invoiceOutboxEvents = getOutboxEvents(documentId);
        assertThat(invoiceOutboxEvents).hasSize(1);

        Map<String, Object> processedEvent = invoiceOutboxEvents.get(0);
        assertThat(processedEvent.get("aggregate_type")).isEqualTo("ProcessedInvoice");
        assertThat(processedEvent.get("aggregate_id")).isEqualTo(documentId);
        assertThat(processedEvent.get("status")).isEqualTo("PENDING");
        assertThat(processedEvent.get("topic")).isEqualTo("invoice.processed");

        // Verify saga reply outbox event (uses sagaId as aggregate_id)
        List<Map<String, Object>> sagaOutboxEvents = getOutboxEvents(sagaId);
        assertThat(sagaOutboxEvents).hasSize(1);

        Map<String, Object> replyEvent = sagaOutboxEvents.get(0);
        assertThat(replyEvent.get("aggregate_type")).isEqualTo("ProcessedInvoice");
        assertThat(replyEvent.get("topic")).isEqualTo("saga.reply.invoice");
        assertThat(replyEvent.get("status")).isEqualTo("PENDING");
    }

    // ========== Outbox Pattern Tests ==========

    @Test
    @DisplayName("Should write InvoiceProcessedEvent to outbox with correct topic")
    void shouldWriteProcessedEventToOutbox() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        getInvoiceBySourceId(documentId);

        List<Map<String, Object>> outboxEvents = getOutboxEvents(documentId);
        assertThat(outboxEvents).hasSize(1);

        Map<String, Object> event = outboxEvents.get(0);
        assertThat(event.get("topic")).isEqualTo("invoice.processed");
        assertThat(event.get("aggregate_type")).isEqualTo("ProcessedInvoice");
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("payload")).isNotNull();
    }

    // ========== CDC Publish Tests ==========

    @Test
    @DisplayName("Should publish invoice.processed event via CDC to Kafka")
    void shouldPublishInvoiceProcessedEventViaCdc() throws Exception {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String sagaId = "saga-" + correlationId;

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then — poll Kafka for CDC-published message
        // InvoiceEventPublisher uses documentId (sourceInvoiceId) as partition key
        getInvoiceBySourceId(documentId);

        await().atMost(2, MINUTES).pollInterval(5, SECONDS).until(() -> {
            pollMessages();
            return hasMessageOnTopic("invoice.processed", documentId);
        });

        List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("invoice.processed", documentId);
        assertThat(messages).isNotEmpty();

        ConsumerRecord<String, String> record = messages.get(0);
        JsonNode payload = parseJson(record.value());

        assertThat(payload.has("documentId")).isTrue();
        assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
        assertThat(payload.get("documentNumber").asText()).isEqualTo(invoiceNumber);
        assertThat(new java.math.BigDecimal(payload.get("totalAmount").asText())
            .compareTo(new java.math.BigDecimal("64200.00"))).isZero();
        assertThat(payload.get("currency").asText()).isEqualTo("THB");
    }

    @Test
    @DisplayName("Should publish saga.reply.invoice event via CDC to Kafka")
    void shouldPublishSagaReplyViaCdc() throws Exception {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String sagaId = "saga-" + correlationId;

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then — poll Kafka for CDC-published reply
        await().atMost(2, MINUTES).pollInterval(5, SECONDS).until(() -> {
            pollMessages();
            return hasMessageOnTopic("saga.reply.invoice", sagaId);
        });

        List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("saga.reply.invoice", sagaId);
        assertThat(messages).isNotEmpty();

        ConsumerRecord<String, String> record = messages.get(0);
        JsonNode payload = parseJson(record.value());

        assertThat(payload.has("status")).isTrue();
        assertThat(payload.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
    }

    // ========== Compensation CDC Tests ==========

    @Test
    @DisplayName("Should delete invoice from database on compensation")
    void shouldDeleteInvoiceOnCompensation() {
        // Given — create and verify invoice exists
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();

        ProcessInvoiceCommand processCommand = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);
        sagaCommandHandler.handleProcessCommand(processCommand);

        Map<String, Object> invoice = getInvoiceBySourceId(documentId);
        assertThat(invoice).isNotNull();

        // When — compensate with a NEW saga instance (different correlationId)
        String compensateCorrelationId = UUID.randomUUID().toString();
        CompensateInvoiceCommand compensateCommand = createCompensateInvoiceCommand(
            documentId, compensateCorrelationId);
        sagaCommandHandler.handleCompensation(compensateCommand);

        // Then — invoice should be deleted
        await().atMost(2, MINUTES).pollInterval(1, SECONDS).until(() -> {
            return getInvoiceBySourceId(documentId) == null;
        });
        assertThat(getInvoiceBySourceId(documentId)).isNull();
    }

    @Test
    @DisplayName("Should publish COMPENSATED reply via CDC after compensation")
    void shouldPublishCompensatedReplyViaCdc() throws Exception {
        // Given — process first so there is something to compensate
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String processCorrelationId = UUID.randomUUID().toString();

        ProcessInvoiceCommand processCommand = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), processCorrelationId);
        sagaCommandHandler.handleProcessCommand(processCommand);

        assertThat(getInvoiceBySourceId(documentId)).isNotNull();

        // When — compensate with a NEW saga instance (different correlationId)
        String compensateCorrelationId = UUID.randomUUID().toString();
        String compensateSagaId = "saga-" + compensateCorrelationId;

        CompensateInvoiceCommand compensateCommand = createCompensateInvoiceCommand(
            documentId, compensateCorrelationId);
        sagaCommandHandler.handleCompensation(compensateCommand);

        // Then — poll Kafka for CDC-published COMPENSATED reply
        await().atMost(2, MINUTES).pollInterval(5, SECONDS).until(() -> {
            pollMessages();
            return hasMessageOnTopic("saga.reply.invoice", compensateSagaId);
        });

        List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("saga.reply.invoice", compensateSagaId);
        assertThat(messages).isNotEmpty();

        // Get the last message (COMPENSATED)
        ConsumerRecord<String, String> record = messages.get(messages.size() - 1);
        JsonNode payload = parseJson(record.value());

        assertThat(payload.has("status")).isTrue();
        assertThat(payload.get("status").asText()).isEqualTo("COMPENSATED");
        assertThat(payload.get("sagaId").asText()).isEqualTo(compensateSagaId);
    }
}
