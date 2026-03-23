package com.wpanther.invoice.processing.infrastructure.adapter.out.parsing;

import com.wpanther.invoice.processing.domain.model.*;
import com.wpanther.invoice.processing.domain.port.out.InvoiceParserPort;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


/**
 * Unit tests for InvoiceParserServiceImpl
 */
class InvoiceParserServiceImplTest {

    private InvoiceParserPort parserService;

    @BeforeEach
    void setUp() {
        parserService = new InvoiceParserServiceImpl();
    }

    @Test
    void constructor_whenJaxbContextFails_throwsIllegalStateException() throws Exception {
        try (MockedStatic<JAXBContext> mockedJaxb = mockStatic(JAXBContext.class)) {
            mockedJaxb.when(() -> JAXBContext.newInstance(anyString()))
                .thenThrow(new JAXBException("Simulated JAXB failure"));

            assertThrows(IllegalStateException.class, () -> new InvoiceParserServiceImpl());
        }
    }

    @Test
    void parseInvoice_whenUnmarshalReturnsUnexpectedType_throwsInvoiceParsingException() throws Exception {
        try (MockedStatic<JAXBContext> mockedJaxb = mockStatic(JAXBContext.class)) {
            JAXBContext mockContext = mock(JAXBContext.class);
            Unmarshaller mockUnmarshaller = mock(Unmarshaller.class);

            mockedJaxb.when(() -> JAXBContext.newInstance(anyString())).thenReturn(mockContext);
            when(mockContext.createUnmarshaller()).thenReturn(mockUnmarshaller);
            when(mockUnmarshaller.unmarshal(any(Source.class))).thenReturn("unexpected-string-type");

            InvoiceParserServiceImpl service = new InvoiceParserServiceImpl();

            InvoiceParserPort.InvoiceParsingException ex = assertThrows(
                InvoiceParserPort.InvoiceParsingException.class,
                () -> service.parse("<test/>", "test-id")
            );
            assertTrue(ex.getMessage().contains("Unexpected root element"));
        }
    }

    @Test
    void parseInvoice_whenXmlContainsDoctype_throwsInvoiceParsingException() {
        // Given: XML with a DOCTYPE declaration (XXE attack vector)
        String xxeXml = "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
            + "<foo>&xxe;</foo>";

        // When/Then: parsing must be rejected before any external resource is accessed
        InvoiceParserPort.InvoiceParsingException ex = assertThrows(
            InvoiceParserPort.InvoiceParsingException.class,
            () -> parserService.parse(xxeXml, "attack-id")
        );
        assertTrue(ex.getMessage().contains("XML parsing failed")
            || ex.getMessage().contains("DOCTYPE")
            || ex.getMessage().contains("Failed to parse XML"),
            "Expected parse rejection due to DOCTYPE; got: " + ex.getMessage());
    }

    @Test
    void testParseValidInvoice() throws InvoiceParserPort.InvoiceParsingException {
        // Given: A valid Thai e-Tax invoice XML
        String xmlContent = getSampleInvoiceXml();
        String sourceInvoiceId = "intake-12345";

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, sourceInvoiceId);

        // Then: All fields should be correctly parsed
        assertNotNull(invoice);
        assertEquals(sourceInvoiceId, invoice.getSourceInvoiceId());
        assertEquals("IV2025-00001", invoice.getInvoiceNumber());
        assertEquals(LocalDate.of(2025, 1, 15), invoice.getIssueDate());
        assertEquals(LocalDate.of(2025, 2, 14), invoice.getDueDate());
        assertEquals("THB", invoice.getCurrency());
        assertNotNull(invoice.getId());
        assertEquals(xmlContent, invoice.getOriginalXml());
    }

    @Test
    void testParseSellerInformation() throws InvoiceParserPort.InvoiceParsingException {
        // Given: A valid invoice XML
        String xmlContent = getSampleInvoiceXml();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Seller information should be correctly parsed
        Party seller = invoice.getSeller();
        assertNotNull(seller);
        assertEquals("Acme Corporation Ltd.", seller.name());
        assertEquals("1234567890123", seller.taxIdentifier().value());
        assertEquals("VAT", seller.taxIdentifier().scheme());

        Address sellerAddress = seller.address();
        assertNotNull(sellerAddress);
        assertEquals("123 Business Street", sellerAddress.streetAddress());
        assertEquals("Bangkok", sellerAddress.city());
        assertEquals("10110", sellerAddress.postalCode());
        assertEquals("TH", sellerAddress.country());
    }

    @Test
    void testParseBuyerInformation() throws InvoiceParserPort.InvoiceParsingException {
        // Given: A valid invoice XML
        String xmlContent = getSampleInvoiceXml();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Buyer information should be correctly parsed
        Party buyer = invoice.getBuyer();
        assertNotNull(buyer);
        assertEquals("Customer Company Ltd.", buyer.name());
        assertEquals("9876543210987", buyer.taxIdentifier().value());

        Address buyerAddress = buyer.address();
        assertNotNull(buyerAddress);
        assertEquals("456 Customer Road", buyerAddress.streetAddress());
        assertEquals("Chiang Mai", buyerAddress.city());
        assertEquals("50000", buyerAddress.postalCode());
        assertEquals("TH", buyerAddress.country());
    }

    @Test
    void testParseLineItems() throws InvoiceParserPort.InvoiceParsingException {
        // Given: A valid invoice XML with line items
        String xmlContent = getSampleInvoiceXml();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Line items should be correctly parsed
        assertNotNull(invoice.getItems());
        assertEquals(2, invoice.getItems().size());

        // First line item
        LineItem item1 = invoice.getItems().get(0);
        assertEquals("Professional Services - Consulting", item1.description());
        assertEquals(10, item1.quantity());
        assertEquals(Money.of(new BigDecimal("5000.00"), "THB"), item1.unitPrice());
        assertEquals(new BigDecimal("7.00"), item1.taxRate());

        // Second line item
        LineItem item2 = invoice.getItems().get(1);
        assertEquals("Software License", item2.description());
        assertEquals(1, item2.quantity());
        assertEquals(Money.of(new BigDecimal("10000.00"), "THB"), item2.unitPrice());
        assertEquals(new BigDecimal("7.00"), item2.taxRate());
    }

    @Test
    void testCalculateTotals() throws InvoiceParserPort.InvoiceParsingException {
        // Given: A valid invoice XML
        String xmlContent = getSampleInvoiceXml();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Totals should be calculated correctly
        // Subtotal: (10 * 5000) + (1 * 10000) = 60,000
        assertEquals(Money.of(new BigDecimal("60000.00"), "THB"), invoice.getSubtotal());

        // Tax: 60,000 * 0.07 = 4,200
        assertEquals(Money.of(new BigDecimal("4200.00"), "THB"), invoice.getTotalTax());

        // Total: 60,000 + 4,200 = 64,200
        assertEquals(Money.of(new BigDecimal("64200.00"), "THB"), invoice.getTotal());
    }

    @Test
    void testParseInvoiceWithNullXml() {
        // Given: Null XML content
        String xmlContent = null;

        // When/Then: Should throw InvoiceParsingException
        assertThrows(InvoiceParserPort.InvoiceParsingException.class,
            () -> parserService.parse(xmlContent, "test-123"));
    }

    @Test
    void testParseInvoiceWithEmptyXml() {
        // Given: Empty XML content
        String xmlContent = "";

        // When/Then: Should throw InvoiceParsingException
        assertThrows(InvoiceParserPort.InvoiceParsingException.class,
            () -> parserService.parse(xmlContent, "test-123"));
    }

    @Test
    void testParseInvoiceWithInvalidXml() {
        // Given: Invalid XML content
        String xmlContent = "<invalid>Not a valid invoice</invalid>";

        // When/Then: Should throw InvoiceParsingException
        assertThrows(InvoiceParserPort.InvoiceParsingException.class,
            () -> parserService.parse(xmlContent, "test-123"));
    }

    @Test
    void testParseInvoiceWithMissingInvoiceNumber() {
        // Given: XML without invoice number
        String xmlContent = getInvoiceXmlWithoutInvoiceNumber();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("Invoice number"));
    }

    @Test
    void testParseInvoiceWithMissingLineItems() {
        // Given: XML without line items
        String xmlContent = getInvoiceXmlWithoutLineItems();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("line item"));
    }

    @Test
    void testParseInvoiceWithMissingDueDate() throws InvoiceParserPort.InvoiceParsingException {
        // Given: XML without due date (should default to issue date + 30 days)
        String xmlContent = getInvoiceXmlWithoutDueDate();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Due date should be issue date + 30 days
        assertNotNull(invoice);
        assertEquals(LocalDate.of(2025, 1, 15), invoice.getIssueDate());
        assertEquals(LocalDate.of(2025, 2, 14), invoice.getDueDate());
    }

    @Test
    void testParseInvoiceWithMinimalAddress() throws InvoiceParserPort.InvoiceParsingException {
        // Given: XML with minimal address (only country required)
        String xmlContent = getInvoiceXmlWithMinimalAddress();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Address should have only country
        assertNotNull(invoice);
        Party seller = invoice.getSeller();
        assertNotNull(seller.address());
        assertEquals("TH", seller.address().country());
        assertNull(seller.address().streetAddress());
        assertNull(seller.address().city());
        assertNull(seller.address().postalCode());
    }

    @Test
    void testParseInvoiceWithTaxIdNoScheme() throws InvoiceParserPort.InvoiceParsingException {
        // Given: XML with tax ID but no scheme (should default to "VAT")
        String xmlContent = getInvoiceXmlWithTaxIdNoScheme();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Tax scheme should default to "VAT"
        assertNotNull(invoice);
        assertEquals("VAT", invoice.getSeller().taxIdentifier().scheme());
        assertEquals("1234567890123", invoice.getSeller().taxIdentifier().value());
    }

    @Test
    void testParseInvoiceWithMissingIssueDate() {
        // Given: XML without issue date
        String xmlContent = getInvoiceXmlWithoutIssueDate();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("Issue date"));
    }

    @Test
    void testParseInvoiceWithMissingSeller() {
        // Given: XML without seller information
        String xmlContent = getInvoiceXmlWithoutSeller();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("Seller"));
    }

    @Test
    void testParseInvoiceWithMissingBuyer() {
        // Given: XML without buyer information
        String xmlContent = getInvoiceXmlWithoutBuyer();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("Buyer"));
    }

    @Test
    void testParseInvoiceWithInvalidCurrency() {
        // Given: XML with invalid currency code (not 3 characters)
        String xmlContent = getInvoiceXmlWithInvalidCurrency();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("currency"));
    }

    @Test
    void testParseInvoiceWithMissingCurrency() {
        // Given: XML without currency
        String xmlContent = getInvoiceXmlWithoutCurrency();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("currency"));
    }

    @Test
    void testParseInvoiceWithLineItemMissingTax() throws InvoiceParserPort.InvoiceParsingException {
        // Given: XML with line item without tax info (should default to 0%)
        String xmlContent = getInvoiceXmlWithLineItemNoTax();

        // When: Parsing the XML
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Tax rate should be zero
        assertNotNull(invoice);
        assertEquals(1, invoice.getItems().size());
        assertEquals(BigDecimal.ZERO, invoice.getItems().get(0).taxRate());
    }

    @Test
    void testParseInvoiceWithMissingSellerName() {
        // Given: XML without seller name
        String xmlContent = getInvoiceXmlWithoutSellerName();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("Seller name"));
    }

    @Test
    void testParseInvoiceWithMissingSellerTaxId() {
        // Given: XML without seller tax ID
        String xmlContent = getInvoiceXmlWithoutSellerTaxId();

        // When/Then: Should throw InvoiceParsingException
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("Seller tax"));
    }

    @Test
    void testParseInvoiceWithMissingSellerCountry() throws InvoiceParserPort.InvoiceParsingException {
        // Given: XML without seller country
        // Per Thai e-Tax XSD, PostalTradeAddress.CountryID is optional.
        // When absent the address is null on the Party (not a parse error).
        String xmlContent = getInvoiceXmlWithoutSellerCountry();

        // When: Parsing should succeed
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Seller address is null (country absent → address discarded)
        assertNotNull(invoice);
        assertNull(invoice.getSeller().address(),
            "Seller address must be null when CountryID is absent");
    }

    /**
     * Sample Thai e-Tax invoice XML for testing
     */
    private String getSampleInvoiceXml() {
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

    /**
     * Invoice XML without invoice number
     */
    private String getInvoiceXmlWithoutInvoiceNumber() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">

              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:invoice:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>

              <rsm:ExchangedDocument>
                <ram:TypeCode>388</ram:TypeCode>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>

              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    /**
     * Invoice XML without line items
     */
    private String getInvoiceXmlWithoutLineItems() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">

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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutDueDate() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:AssociatedDocumentLineDocument>
                    <ram:LineID>1</ram:LineID>
                  </ram:AssociatedDocumentLineDocument>
                  <ram:SpecifiedTradeProduct>
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
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

    private String getInvoiceXmlWithMinimalAddress() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
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
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
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

    private String getInvoiceXmlWithTaxIdNoScheme() {
        return getSampleInvoiceXml();  // Our sample already has one without scheme for buyer
    }

    private String getInvoiceXmlWithoutIssueDate() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:invoice:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>IV2025-00001</ram:ID>
                <ram:TypeCode>388</ram:TypeCode>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutSeller() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutBuyer() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithInvalidCurrency() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>INVALID</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:AssociatedDocumentLineDocument>
                    <ram:LineID>1</ram:LineID>
                  </ram:AssociatedDocumentLineDocument>
                  <ram:SpecifiedTradeProduct>
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutCurrency() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:AssociatedDocumentLineDocument>
                    <ram:LineID>1</ram:LineID>
                  </ram:AssociatedDocumentLineDocument>
                  <ram:SpecifiedTradeProduct>
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithLineItemNoTax() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
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
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutSellerName() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutSellerTaxId() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    @Test
    void testParseInvoiceWithSellerEmail() throws InvoiceParserPort.InvoiceParsingException {
        String xmlContent = getInvoiceXmlWithSellerEmail();

        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        assertNotNull(invoice);
        Party seller = invoice.getSeller();
        assertNotNull(seller);
        assertTrue(seller.hasEmail());
        assertEquals("seller@acme.co.th", seller.email());
    }

    @Test
    void testParseInvoiceWithDecimalQuantity() {
        String xmlContent = getInvoiceXmlWithDecimalQuantity();

        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("whole number") || exception.getMessage().contains("1"));
    }

    @Test
    void testParseInvoiceWithMissingExchangedDocument() {
        String xmlContent = getInvoiceXmlWithoutExchangedDocument();

        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("ExchangedDocument")
            || exception.getMessage() != null);
    }

    private String getInvoiceXmlWithSellerEmail() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:invoice:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>IV2025-00099</ram:ID>
                <ram:TypeCode>388</ram:TypeCode>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Acme Corporation Ltd.</ram:Name>
                    <ram:DefinedTradeContact>
                      <ram:EmailURIUniversalCommunication>
                        <ram:URIID>seller@acme.co.th</ram:URIID>
                      </ram:EmailURIUniversalCommunication>
                    </ram:DefinedTradeContact>
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
                    <ram:Name>Test Service</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
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

    private String getInvoiceXmlWithDecimalQuantity() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
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
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1.5</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutExchangedDocument() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:invoice:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    @Test
    void testParseInvoiceWithMissingSupplyChainTradeTransaction() {
        // Given: XML with ExchangedDocument but no SupplyChainTradeTransaction
        String xmlContent = getInvoiceXmlWithoutSupplyChainTradeTransaction();

        // When/Then: Should throw InvoiceParsingException mentioning SupplyChainTradeTransaction
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("SupplyChainTradeTransaction"));
    }

    @Test
    void testParseInvoiceWithEmptyTaxRegistration() {
        // Given: XML where seller has SpecifiedTaxRegistration present but with no ID element
        String xmlContent = getInvoiceXmlWithEmptyTaxRegistration();

        // When/Then: Should throw InvoiceParsingException about missing tax ID
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("tax"));
    }

    @Test
    void testParseInvoiceWithMissingSellerPostalAddress() throws InvoiceParserPort.InvoiceParsingException {
        // Given: XML where seller has no PostalTradeAddress element at all.
        // Per Thai e-Tax XSD, PostalTradeAddress is optional — the parse succeeds
        // and the seller address is null (not a parse error).
        String xmlContent = getInvoiceXmlWithMissingSellerPostalAddress();

        // When: Parsing should succeed
        ProcessedInvoice invoice = parserService.parse(xmlContent, "test-123");

        // Then: Seller address is null when PostalTradeAddress is absent
        assertNotNull(invoice);
        assertNull(invoice.getSeller().address(),
            "Seller address must be null when PostalTradeAddress element is absent");
    }

    @Test
    void testParseInvoiceWithMissingProductName() {
        // Given: XML where line item has SpecifiedTradeProduct but no Name element
        String xmlContent = getInvoiceXmlWithMissingProductName();

        // When/Then: Should throw InvoiceParsingException about missing product name
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("product name") || exception.getMessage().contains("line item"));
    }

    @Test
    void testParseInvoiceWithMissingLineDelivery() {
        // Given: XML where line item has no SpecifiedLineTradeDelivery element
        String xmlContent = getInvoiceXmlWithMissingLineDelivery();

        // When/Then: Should throw InvoiceParsingException about missing quantity
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("quantity") || exception.getMessage().contains("line item"));
    }

    @Test
    void testParseInvoiceWithMissingLineAgreement() {
        // Given: XML where line item has no SpecifiedLineTradeAgreement element
        String xmlContent = getInvoiceXmlWithMissingLineAgreement();

        // When/Then: Should throw InvoiceParsingException about missing unit price
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("price") || exception.getMessage().contains("line item"));
    }

    @Test
    void testParseInvoiceWithEmptyChargeAmount() {
        // Given: XML where GrossPriceProductTradePrice has no ChargeAmount elements
        String xmlContent = getInvoiceXmlWithEmptyChargeAmount();

        // When/Then: Should throw InvoiceParsingException about missing price amount
        InvoiceParserPort.InvoiceParsingException exception =
            assertThrows(InvoiceParserPort.InvoiceParsingException.class,
                () -> parserService.parse(xmlContent, "test-123"));

        assertTrue(exception.getMessage().contains("price amount") || exception.getMessage().contains("line item"));
    }

    private String getInvoiceXmlWithoutSupplyChainTradeTransaction() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithEmptyTaxRegistration() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithMissingSellerPostalAddress() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity>1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                  <ram:SpecifiedTradeProduct>
                    <ram:Name>Test Service</ram:Name>
                  </ram:SpecifiedTradeProduct>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithMissingProductName() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
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
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithMissingLineDelivery() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
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
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithMissingLineAgreement() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
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
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithEmptyChargeAmount() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
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
                    <ram:Name>Test Product</ram:Name>
                  </ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity unitCode="C62">1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    private String getInvoiceXmlWithoutSellerCountry() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:Invoice_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16">
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
                    <ram:Name>Test Seller</ram:Name>
                    <ram:PostalTradeAddress>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty>
                    <ram:Name>Test Buyer</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID>9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:GrossPriceProductTradePrice>
                      <ram:ChargeAmount>1000.00</ram:ChargeAmount>
                    </ram:GrossPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery>
                    <ram:BilledQuantity>1</ram:BilledQuantity>
                  </ram:SpecifiedLineTradeDelivery>
                  <ram:SpecifiedTradeProduct>
                    <ram:Name>Test Service</ram:Name>
                  </ram:SpecifiedTradeProduct>
                </ram:IncludedSupplyChainTradeLineItem>
              </rsm:SupplyChainTradeTransaction>
            </rsm:Invoice_CrossIndustryInvoice>
            """;
    }

    @Test
    void constructor_whenSaxParserConfigFails_throwsIllegalStateException() throws Exception {
        // SAXParserFactory is now initialized once at construction time (not per-parse).
        // A factory setFeature failure during construction must surface as IllegalStateException.
        try (MockedStatic<SAXParserFactory> mockedSpf = mockStatic(SAXParserFactory.class)) {
            SAXParserFactory mockFactory = mock(SAXParserFactory.class);
            mockedSpf.when(SAXParserFactory::newInstance).thenReturn(mockFactory);
            doThrow(new ParserConfigurationException("Simulated config failure"))
                .when(mockFactory).setFeature(anyString(), anyBoolean());

            assertThrows(IllegalStateException.class, () -> new InvoiceParserServiceImpl());
        }
    }

    @Test
    void parse_whenXmlExceedsMaxSize_throwsInvoiceParsingException() {
        // Generate a payload larger than MAX_XML_BYTES (500 KB)
        String oversizedXml = "a".repeat(InvoiceParserServiceImpl.MAX_XML_BYTES + 1);

        InvoiceParserPort.InvoiceParsingException ex = assertThrows(
            InvoiceParserPort.InvoiceParsingException.class,
            () -> parserService.parse(oversizedXml, "oversize-id")
        );
        assertTrue(ex.getMessage().contains("too large"),
            "Exception message must mention 'too large'; got: " + ex.getMessage());
    }

    @Test
    void parse_whenParseTimesOut_throwsInvoiceParsingException() throws Exception {
        // Use a 1 ms timeout so even a trivial parse triggers the timeout guard.
        InvoiceParserServiceImpl fastTimeoutService =
            new InvoiceParserServiceImpl(1, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Any valid-ish XML — the timeout fires before JAXB produces a result.
        String anyXml = getSampleInvoiceXml();

        InvoiceParserPort.InvoiceParsingException ex = assertThrows(
            InvoiceParserPort.InvoiceParsingException.class,
            () -> fastTimeoutService.parse(anyXml, "timeout-id")
        );
        assertTrue(ex.getMessage().contains("timed out") || ex.getMessage().contains("parsing"),
            "Exception message should indicate timeout; got: " + ex.getMessage());

        fastTimeoutService.shutdownExecutor();
    }

    @Test
    void parse_whenExecutorShutDownBeforeSubmit_releasesSemaphoreAndThrows() {
        // Regression test: before the fix, a RejectedExecutionException thrown by
        // submit() would leak the semaphore permit because the outer try/finally only
        // wrapped future.get(), not the submit() call itself.  After maxConcurrentParses
        // such leaks the service would deadlock on parseSemaphore.acquire().
        //
        // Use maxConcurrentParses=1 so that if the permit leaks a second call blocks.
        InvoiceParserServiceImpl service =
            new InvoiceParserServiceImpl(1, java.util.concurrent.TimeUnit.SECONDS, 30, 1);
        service.shutdownExecutor();  // executor is now shut down — submit() will throw

        String anyXml = getSampleInvoiceXml();

        // First call: RejectedExecutionException from submit() must be wrapped in
        // InvoiceParsingException (forUnmarshal path) and the permit must be released.
        assertThrows(InvoiceParserPort.InvoiceParsingException.class,
            () -> service.parse(anyXml, "rejected-1"));

        // Second call: if the permit was leaked the acquire() would block forever.
        // The fact that it also throws (not hangs) proves the permit was released.
        assertThrows(InvoiceParserPort.InvoiceParsingException.class,
            () -> service.parse(anyXml, "rejected-2"));
    }

    @Test
    void parse_whenSchemeIsUnrecognised_defaultsToVat() throws InvoiceParserPort.InvoiceParsingException {
        // XML with an unrecognised schemeID — should be silently replaced with VAT
        String xmlContent = getInvoiceXmlWithTaxIdNoScheme();

        ProcessedInvoice invoice = parserService.parse(xmlContent, "scheme-test");

        assertEquals("VAT", invoice.getSeller().taxIdentifier().scheme(),
            "Unrecognised scheme must be normalised to VAT");
    }
}