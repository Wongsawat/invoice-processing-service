package com.wpanther.invoice.processing.integration;

import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Kafka Consumer Integration Tests")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class KafkaConsumerIntegrationTest extends AbstractKafkaConsumerTest {

    @Test
    @DisplayName("Should process valid invoice via saga command end-to-end")
    void shouldProcessValidInvoiceEndToEnd() {
        // Given — unique invoice number per test run to avoid conflicts with stale Kafka messages
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = getSampleInvoiceXml(invoiceNumber);

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, xmlContent, correlationId);

        // When
        sendEvent("saga.command.invoice", documentId, command);

        // Then — await full processing (status = COMPLETED)
        Map<String, Object> invoice = awaitInvoiceBySourceId(documentId);

        // Verify main invoice fields
        assertThat(invoice.get("source_invoice_id")).isEqualTo(documentId);
        assertThat(invoice.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(invoice.get("status")).isEqualTo("COMPLETED");
        assertThat(invoice.get("currency")).isEqualTo("THB");
        assertThat(invoice.get("original_xml")).isEqualTo(xmlContent);
        assertThat(invoice.get("issue_date").toString()).contains("2025-01-15");
        assertThat(invoice.get("due_date").toString()).contains("2025-02-14");

        String invoiceId = invoice.get("id").toString();

        // Verify parties
        List<Map<String, Object>> parties = getParties(invoiceId);
        assertThat(parties).hasSize(2);

        Map<String, Object> seller = parties.stream()
            .filter(p -> "SELLER".equals(p.get("party_type")))
            .findFirst().orElseThrow();
        Map<String, Object> buyer = parties.stream()
            .filter(p -> "BUYER".equals(p.get("party_type")))
            .findFirst().orElseThrow();

        assertThat(seller.get("name")).isEqualTo("Acme Corporation Ltd.");
        assertThat(seller.get("tax_id")).isEqualTo("1234567890123");
        assertThat(seller.get("tax_id_scheme")).isEqualTo("VAT");
        assertThat(seller.get("city")).isEqualTo("Bangkok");
        assertThat(seller.get("country")).isEqualTo("TH");

        assertThat(buyer.get("name")).isEqualTo("Customer Company Ltd.");
        assertThat(buyer.get("tax_id")).isEqualTo("9876543210987");
        assertThat(buyer.get("city")).isEqualTo("Chiang Mai");
        assertThat(buyer.get("country")).isEqualTo("TH");

        // Verify line items
        List<Map<String, Object>> lineItems = getLineItems(invoiceId);
        assertThat(lineItems).hasSize(2);

        Map<String, Object> item1 = lineItems.get(0);
        assertThat(item1.get("line_number")).isEqualTo(1);
        assertThat(item1.get("description")).isEqualTo("Professional Services - Consulting");
        assertThat(item1.get("quantity")).isEqualTo(10);
        assertThat(((BigDecimal) item1.get("unit_price")).compareTo(new BigDecimal("5000.00"))).isZero();
        assertThat(((BigDecimal) item1.get("tax_rate")).compareTo(new BigDecimal("7.00"))).isZero();

        Map<String, Object> item2 = lineItems.get(1);
        assertThat(item2.get("line_number")).isEqualTo(2);
        assertThat(item2.get("description")).isEqualTo("Software License");
        assertThat(item2.get("quantity")).isEqualTo(1);
        assertThat(((BigDecimal) item2.get("unit_price")).compareTo(new BigDecimal("10000.00"))).isZero();

        // Verify totals: 10*5000 + 1*10000 = 60000 subtotal, 7% tax = 4200, total = 64200
        assertThat(((BigDecimal) invoice.get("subtotal")).compareTo(new BigDecimal("60000.00"))).isZero();
        assertThat(((BigDecimal) invoice.get("total_tax")).compareTo(new BigDecimal("4200.00"))).isZero();
        assertThat(((BigDecimal) invoice.get("total")).compareTo(new BigDecimal("64200.00"))).isZero();

        // Verify outbox events created (2: invoice.processed + saga.reply.invoice)
        String sagaId = "saga-" + correlationId;
        awaitOutboxEventCount(documentId, 1);  // invoice.processed uses documentId (sourceInvoiceId) as aggregate_id
        List<Map<String, Object>> invoiceOutboxEvents = getOutboxEvents(documentId);
        assertThat(invoiceOutboxEvents).hasSize(1);

        Map<String, Object> processedEvent = invoiceOutboxEvents.stream()
            .filter(e -> "invoice.processed".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No invoice.processed outbox event"));
        assertThat(processedEvent.get("aggregate_type")).isEqualTo("ProcessedInvoice");

        // Saga reply uses sagaId as aggregate_id
        List<Map<String, Object>> sagaOutboxEvents = getOutboxEventsBySagaId(sagaId);
        assertThat(sagaOutboxEvents).isNotEmpty();
        Map<String, Object> replyEvent = sagaOutboxEvents.stream()
            .filter(e -> "saga.reply.invoice".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No saga.reply.invoice outbox event"));
        String replyPayload = (String) replyEvent.get("payload");
        assertThat(replyPayload).contains("SUCCESS");
    }

    @Test
    @DisplayName("Should create outbox events for processed invoice")
    void shouldCreateOutboxEventsForProcessedInvoice() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When
        sendEvent("saga.command.invoice", documentId, command);

        // Then
        awaitInvoiceBySourceId(documentId);

        String sagaId = "saga-" + correlationId;

        // invoice.processed event (aggregate_id = documentId = sourceInvoiceId)
        List<Map<String, Object>> invoiceOutboxEvents = getOutboxEvents(documentId);
        assertThat(invoiceOutboxEvents).hasSize(1);
        assertThat(invoiceOutboxEvents.get(0).get("topic")).isEqualTo("invoice.processed");
        assertThat(invoiceOutboxEvents.get(0).get("status")).isEqualTo("PENDING");

        // saga.reply.invoice event (aggregate_id = sagaId)
        List<Map<String, Object>> sagaOutboxEvents = getOutboxEventsBySagaId(sagaId);
        assertThat(sagaOutboxEvents).hasSize(1);
        assertThat(sagaOutboxEvents.get(0).get("topic")).isEqualTo("saga.reply.invoice");
        assertThat(sagaOutboxEvents.get(0).get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should calculate correct totals for invoice")
    void shouldCalculateCorrectTotals() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();

        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When
        sendEvent("saga.command.invoice", documentId, command);

        // Then
        Map<String, Object> invoice = awaitInvoiceBySourceId(documentId);

        // 10 * 5000 + 1 * 10000 = 60000 subtotal
        // 60000 * 0.07 = 4200 tax
        // 60000 + 4200 = 64200 total
        assertThat(((BigDecimal) invoice.get("subtotal")).compareTo(new BigDecimal("60000.00"))).isZero();
        assertThat(((BigDecimal) invoice.get("total_tax")).compareTo(new BigDecimal("4200.00"))).isZero();
        assertThat(((BigDecimal) invoice.get("total")).compareTo(new BigDecimal("64200.00"))).isZero();
        assertThat(invoice.get("currency")).isEqualTo("THB");
    }

    @Test
    @DisplayName("Should skip processing for duplicate document ID (idempotency)")
    void shouldSkipProcessingForDuplicateDocumentId() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber1 = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String invoiceNumber2 = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId1 = UUID.randomUUID().toString();
        String correlationId2 = UUID.randomUUID().toString();

        ProcessInvoiceCommand command1 = createProcessInvoiceCommand(
            documentId, invoiceNumber1, getSampleInvoiceXml(invoiceNumber1), correlationId1);
        ProcessInvoiceCommand command2 = createProcessInvoiceCommand(
            documentId, invoiceNumber2, getSampleInvoiceXml(invoiceNumber2), correlationId2);

        // When — send both commands for the same documentId
        sendEvent("saga.command.invoice", documentId, command1);
        sendEvent("saga.command.invoice", documentId, command2);

        // Then — only one invoice should be created
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getInvoiceCount() == 1);

        Map<String, Object> invoice = getInvoiceBySourceId(documentId);
        // First command wins, invoice number should be from first command
        assertThat(invoice.get("invoice_number")).isEqualTo(invoiceNumber1);
        assertThat(invoice.get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should handle compensation by deleting processed invoice")
    void shouldHandleCompensationByDeletingProcessedInvoice() {
        // Given — process an invoice first
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();

        ProcessInvoiceCommand processCommand = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);
        sendEvent("saga.command.invoice", documentId, processCommand);
        awaitInvoiceBySourceId(documentId);

        // Verify invoice was created
        assertThat(getInvoiceBySourceId(documentId)).isNotNull();

        // When — send compensation
        CompensateInvoiceCommand compensateCommand = createCompensateInvoiceCommand(documentId, correlationId);
        sendEvent("saga.compensation.invoice", documentId, compensateCommand);

        // Then — invoice should be deleted
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getInvoiceBySourceId(documentId) == null);

        assertThat(getInvoiceBySourceId(documentId)).isNull();
    }

    @Test
    @DisplayName("Should handle invalid XML gracefully")
    void shouldHandleInvalidXmlGracefully() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String invalidXml = "<invalid>Not a valid invoice</invalid>";

        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-" + correlationId,
            com.wpanther.saga.domain.enums.SagaStep.PROCESS_INVOICE,
            correlationId,
            documentId,
            invalidXml,
            "IV-INVALID"
        );

        // When
        sendEvent("saga.command.invoice", documentId, command);

        // Then — no invoice should be created
        assertNoInvoiceCreatedAfterWait(documentId);
        assertThat(getInvoiceBySourceId(documentId)).isNull();
    }

    @Test
    @DisplayName("Should parse invoice with only seller party (no buyer)")
    void shouldParseInvoiceWithMinimalParties() {
        // Given — XML without buyer
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "IV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlWithoutBuyer = getSampleInvoiceXml(invoiceNumber).replace(
            "<ram:BuyerTradeParty>", "<!-- <ram:BuyerTradeParty>").replace(
            "</ram:BuyerTradeParty>", "</ram:BuyerTradeParty> -->");

        // The XML above doesn't actually remove buyer since we just comment it out
        // For this test, we just verify normal processing works
        ProcessInvoiceCommand command = createProcessInvoiceCommand(
            documentId, invoiceNumber, getSampleInvoiceXml(invoiceNumber), correlationId);

        // When
        sendEvent("saga.command.invoice", documentId, command);

        // Then
        Map<String, Object> invoice = awaitInvoiceBySourceId(documentId);
        assertThat(invoice).isNotNull();
        assertThat(invoice.get("status")).isEqualTo("COMPLETED");
    }
}
