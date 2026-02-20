package com.wpanther.taxinvoice.processing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.taxinvoice.processing.application.service.SagaCommandHandler;
import com.wpanther.taxinvoice.processing.domain.event.ProcessTaxInvoiceCommand;
import com.wpanther.taxinvoice.processing.integration.config.CdcTestConfiguration;
import com.wpanther.taxinvoice.processing.integration.config.TestKafkaConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.HttpURLConnection;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(
    classes = CdcTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("cdc-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
public abstract class AbstractCdcIntegrationTest {

    private static final String POSTGRES_URL = "jdbc:postgresql://localhost:5433/taxinvoiceprocess_db";
    private static final String POSTGRES_USER = "postgres";
    private static final String POSTGRES_PASSWORD = "postgres";
    private static final String KAFKA_BOOTSTRAP = "localhost:9093";
    private static final String DEBEZIUM_URL = "http://localhost:8083";
    private static final String CONNECTOR_NAME = "outbox-connector-taxinvoice";

    @Autowired
    protected SagaCommandHandler sagaCommandHandler;

    @Autowired
    protected JdbcTemplate testJdbcTemplate;

    @Autowired
    protected KafkaConsumer<String, String> testKafkaConsumer;

    @Autowired
    protected TestKafkaConsumerConfig kafkaConfig;

    protected ObjectMapper objectMapper;
    protected final ConcurrentHashMap<String, CopyOnWriteArrayList<ConsumerRecord<String, String>>> receivedMessages =
        new ConcurrentHashMap<>();

    @BeforeAll
    void setupInfrastructure() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        verifyExternalContainers();
        verifyDebeziumConnectorRunning();
        kafkaConfig.createTopics();
        subscribeToTopics();
    }

    @BeforeEach
    void cleanupTestData() {
        testJdbcTemplate.execute("DELETE FROM outbox_events");
        testJdbcTemplate.execute("DELETE FROM tax_invoice_line_items");
        testJdbcTemplate.execute("DELETE FROM tax_invoice_parties");
        testJdbcTemplate.execute("DELETE FROM processed_tax_invoices");
        receivedMessages.clear();
    }

    // ========== Infrastructure Verification ==========

    private void verifyExternalContainers() {
        // Verify PostgreSQL
        try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
            conn.createStatement().execute("SELECT 1");
        } catch (Exception e) {
            throw new IllegalStateException(
                "PostgreSQL not available at localhost:5433. Start containers: ./scripts/test-containers-start.sh --with-debezium", e);
        }

        // Verify Kafka
        try (var adminClient = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of("bootstrap.servers", KAFKA_BOOTSTRAP))) {
            adminClient.listTopics().names().get(10, SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka not available at localhost:9093", e);
        }

        // Verify Debezium
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(DEBEZIUM_URL + "/connectors").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            if (conn.getResponseCode() != 200) {
                throw new IllegalStateException("Debezium returned " + conn.getResponseCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                "Debezium not available at localhost:8083. Start with: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors", e);
        }
    }

    private void verifyDebeziumConnectorRunning() {
        await().atMost(2, MINUTES).pollInterval(5, SECONDS).until(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                    URI.create(DEBEZIUM_URL + "/connectors/" + CONNECTOR_NAME + "/status").toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    String body = new String(conn.getInputStream().readAllBytes());
                    return body.contains("\"state\":\"RUNNING\"");
                }
            } catch (Exception ignored) {}
            return false;
        });
    }

    private void subscribeToTopics() {
        testKafkaConsumer.subscribe(Arrays.asList("taxinvoice.processed", "saga.reply.tax-invoice"));
        // Initial poll to trigger partition assignment
        testKafkaConsumer.poll(Duration.ofMillis(500));
    }

    // ========== Kafka Message Polling ==========

    protected void pollMessages() {
        ConsumerRecords<String, String> records = testKafkaConsumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
            receivedMessages
                .computeIfAbsent(record.topic(), k -> new CopyOnWriteArrayList<>())
                .add(record);
        }
    }

    protected boolean hasMessageOnTopic(String topic, String partitionKey) {
        pollMessages();
        CopyOnWriteArrayList<ConsumerRecord<String, String>> messages = receivedMessages.get(topic);
        if (messages == null) return false;
        return messages.stream().anyMatch(r -> partitionKey.equals(r.key()));
    }

    protected List<ConsumerRecord<String, String>> getMessagesFromTopic(String topic, String partitionKey) {
        pollMessages();
        CopyOnWriteArrayList<ConsumerRecord<String, String>> messages = receivedMessages.get(topic);
        if (messages == null) return List.of();
        return messages.stream()
            .filter(r -> partitionKey.equals(r.key()))
            .collect(Collectors.toList());
    }

    protected JsonNode parseJson(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        // Handle Debezium double-encoding: if the root is a text node, re-parse inner JSON
        if (node.isTextual()) {
            node = objectMapper.readTree(node.textValue());
        }
        return node;
    }

    // ========== Database Query Helpers ==========

    protected Map<String, Object> getInvoiceBySourceId(String sourceInvoiceId) {
        List<Map<String, Object>> results = testJdbcTemplate.queryForList(
            "SELECT * FROM processed_tax_invoices WHERE source_invoice_id = ?", sourceInvoiceId);
        return results.isEmpty() ? null : results.get(0);
    }

    protected List<Map<String, Object>> getOutboxEvents(String aggregateId) {
        return testJdbcTemplate.queryForList(
            "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at", aggregateId);
    }

    // ========== Command Creation Helpers ==========

    protected ProcessTaxInvoiceCommand createProcessTaxInvoiceCommand(
            String documentId, String invoiceNumber, String xmlContent, String correlationId) {
        return new ProcessTaxInvoiceCommand(
            "saga-" + correlationId, "process-tax-invoice", correlationId,
            documentId, xmlContent, invoiceNumber
        );
    }

    // ========== Test XML Fixtures ==========

    protected String getSampleTaxInvoiceXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:TaxInvoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16"
                xmlns:qdt="urn:etda:uncefact:data:standard:QualifiedDataType:1">

              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:taxinvoice:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>

              <rsm:ExchangedDocument>
                <ram:ID>TV2025-00001</ram:ID>
                <ram:TypeCode>388</ram:TypeCode>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>

              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Acme Corporation Ltd.</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:LineOne>123 Business Street</ram:LineOne>
                      <ram:CityName>Bangkok</ram:CityName>
                      <ram:PostcodeCode>10110</ram:PostcodeCode>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID schemeID="VAT">1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Customer Company Ltd.</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:LineOne>456 Customer Road</ram:LineOne>
                      <ram:CityName>Chiang Mai</ram:CityName>
                      <ram:PostcodeCode>50000</ram:PostcodeCode>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>

                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                  <ram:SpecifiedTradePaymentTerms>
                    <ram:DueDateDateTime>2025-02-14T00:00:00</ram:DueDateDateTime>
                  </ram:SpecifiedTradePaymentTerms>
                </ram:ApplicableHeaderTradeSettlement>

                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:AssociatedDocumentLineDocument>
                    <ram:LineID>1</ram:LineID>
                  </ram:AssociatedDocumentLineDocument>
                  <ram:SpecifiedTradeProduct>
                    <ram:Name>Professional Services - Consulting</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>5000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="HUR">10</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                  <ram:SpecifiedLineTradeSettlement>
                    <ram:ApplicableTradeTax>
                      <ram:TypeCode>VAT</ram:TypeCode>
                      <ram:CalculatedRate>7.00</ram:CalculatedRate>
                    </ram:ApplicableTradeTax>
                  </ram:SpecifiedLineTradeSettlement>
                </ram:IncludedSupplyChainTradeLineItem>

                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:AssociatedDocumentLineDocument>
                    <ram:LineID>2</ram:LineID>
                  </ram:AssociatedDocumentLineDocument>
                  <ram:SpecifiedTradeProduct>
                    <ram:Name>Software License</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>10000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                  <ram:SpecifiedLineTradeSettlement>
                    <ram:ApplicableTradeTax>
                      <ram:TypeCode>VAT</ram:TypeCode>
                      <ram:CalculatedRate>7.00</ram:CalculatedRate>
                    </ram:ApplicableTradeTax>
                  </ram:SpecifiedLineTradeSettlement>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:TaxInvoice_CrossIndustryInvoice>
            """;
    }

    /**
     * Returns sample XML with the given invoice number replacing the default TV2025-00001.
     */
    protected String getSampleTaxInvoiceXml(String invoiceNumber) {
        return getSampleTaxInvoiceXml().replace("TV2025-00001", invoiceNumber);
    }
}
