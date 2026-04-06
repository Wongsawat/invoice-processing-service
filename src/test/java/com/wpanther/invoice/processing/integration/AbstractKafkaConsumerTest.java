package com.wpanther.invoice.processing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.invoice.processing.integration.config.ConsumerTestConfiguration;
import com.wpanther.invoice.processing.integration.config.TestKafkaProducerConfig;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9093",
        "KAFKA_BROKERS=localhost:9093"
    }
)
@ActiveProfiles("consumer-test")
@Import({TestKafkaProducerConfig.class, ConsumerTestConfiguration.class})
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractKafkaConsumerTest {

    @Autowired
    protected KafkaTemplate<String, String> testKafkaTemplate;

    @Autowired
    protected JdbcTemplate testJdbcTemplate;

    protected ObjectMapper objectMapper;

    @BeforeAll
    void setupObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void cleanupDatabase() {
        // Delete in FK-safe order
        testJdbcTemplate.execute("DELETE FROM outbox_events");
        testJdbcTemplate.execute("DELETE FROM invoice_line_items");
        testJdbcTemplate.execute("DELETE FROM invoice_parties");
        testJdbcTemplate.execute("DELETE FROM processed_invoices");
    }

    // ========== Event Sending ==========

    protected void sendEvent(String topic, String key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            testKafkaTemplate.send(topic, key, json).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send event to topic: " + topic, e);
        }
    }

    protected ProcessInvoiceCommand createProcessInvoiceCommand(
            String documentId, String invoiceNumber, String xmlContent, String correlationId) {
        return new ProcessInvoiceCommand(
            "saga-" + correlationId, SagaStep.PROCESS_INVOICE, correlationId,
            documentId, xmlContent, invoiceNumber
        );
    }

    protected CompensateInvoiceCommand createCompensateInvoiceCommand(
            String documentId, String correlationId) {
        return new CompensateInvoiceCommand(
            "saga-" + correlationId, SagaStep.PROCESS_INVOICE, correlationId,
            SagaStep.PROCESS_INVOICE.name(), documentId, "INVOICE"
        );
    }

    /**
     * Returns sample XML with the given invoice number replacing the default IV2025-00001.
     */
    protected String getSampleInvoiceXml(String invoiceNumber) {
        return getSampleInvoiceXml().replace("IV2025-00001", invoiceNumber);
    }

    // ========== Await Helpers ==========

    protected Map<String, Object> awaitInvoiceBySourceId(String sourceInvoiceId) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> {
                   Map<String, Object> invoice = getInvoiceBySourceId(sourceInvoiceId);
                   return invoice != null && "COMPLETED".equals(invoice.get("status"));
               });
        return getInvoiceBySourceId(sourceInvoiceId);
    }

    protected void awaitOutboxEventCount(String invoiceId, int expectedCount) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getOutboxEvents(invoiceId).size() >= expectedCount);
    }

    protected void assertNoInvoiceCreatedAfterWait(String sourceInvoiceId) {
        await().during(15, TimeUnit.SECONDS)
               .atMost(20, TimeUnit.SECONDS)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getInvoiceBySourceId(sourceInvoiceId) == null);
    }

    // ========== Database Query Helpers ==========

    protected Map<String, Object> getInvoiceBySourceId(String sourceInvoiceId) {
        List<Map<String, Object>> results = testJdbcTemplate.queryForList(
            "SELECT * FROM processed_invoices WHERE source_invoice_id = ?", sourceInvoiceId);
        return results.isEmpty() ? null : results.get(0);
    }

    protected List<Map<String, Object>> getOutboxEvents(String invoiceId) {
        return testJdbcTemplate.queryForList(
            "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
            invoiceId);
    }

    protected List<Map<String, Object>> getOutboxEventsBySagaId(String sagaId) {
        return testJdbcTemplate.queryForList(
            "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
            sagaId);
    }

    protected List<Map<String, Object>> getLineItems(String invoiceId) {
        return testJdbcTemplate.queryForList(
            "SELECT * FROM invoice_line_items WHERE invoice_id = ?::uuid ORDER BY line_number",
            invoiceId);
    }

    protected List<Map<String, Object>> getParties(String invoiceId) {
        return testJdbcTemplate.queryForList(
            "SELECT * FROM invoice_parties WHERE invoice_id = ?::uuid ORDER BY party_type",
            invoiceId);
    }

    protected int getInvoiceCount() {
        Integer count = testJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM processed_invoices", Integer.class);
        return count != null ? count : 0;
    }

    // ========== Test XML Fixtures ==========

    protected String getSampleInvoiceXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16"
                xmlns:qdt="urn:etda:uncefact:data:standard:QualifiedDataType:1">

              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:invoice:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>

              <rsm:ExchangedDocument>
                <ram:ID>IV2025-00001</ram:ID>
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
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }
}
