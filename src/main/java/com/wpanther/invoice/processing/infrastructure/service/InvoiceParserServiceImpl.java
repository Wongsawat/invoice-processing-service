package com.wpanther.invoice.processing.infrastructure.service;

import com.wpanther.invoice.processing.domain.model.*;
import com.wpanther.invoice.processing.domain.service.InvoiceParserService;
import com.wpanther.etax.generated.invoice.ram.*;
import com.wpanther.etax.generated.invoice.rsm.Invoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.invoice.rsm.impl.Invoice_CrossIndustryInvoiceTypeImpl;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of InvoiceParserService that uses teda library's JAXB classes
 * to parse Thai e-Tax invoice XML.
 */
@Slf4j
@Service
public class InvoiceParserServiceImpl implements InvoiceParserService {

    private final JAXBContext jaxbContext;

    public InvoiceParserServiceImpl() throws InvoiceParsingException {
        try {
            // Initialize JAXB context with the implementation package
            // The teda library uses interface/implementation pattern with a custom JAXBContextFactory
            // We need to use the package path to let the factory handle the context creation
            String contextPath = "com.wpanther.etax.generated.invoice.rsm.impl" +
                               ":com.wpanther.etax.generated.invoice.ram.impl" +
                               ":com.wpanther.etax.generated.common.qdt.impl" +
                               ":com.wpanther.etax.generated.common.udt.impl";
            this.jaxbContext = JAXBContext.newInstance(contextPath);
            log.info("JAXB context initialized successfully for Thai e-Tax invoice parsing");
        } catch (JAXBException e) {
            log.error("Failed to initialize JAXB context", e);
            throw new InvoiceParsingException("Failed to initialize XML parser", e);
        }
    }

    @Override
    public ProcessedInvoice parseInvoice(String xmlContent, String sourceInvoiceId)
            throws InvoiceParsingException {

        log.debug("Starting XML parsing for source invoice ID: {}", sourceInvoiceId);

        try {
            // Step 1: Unmarshal XML to JAXB object
            Invoice_CrossIndustryInvoiceType jaxbInvoice = unmarshalXml(xmlContent);

            // Step 2: Extract invoice components
            ExchangedDocumentType document = jaxbInvoice.getExchangedDocument();
            if (document == null) {
                throw new InvoiceParsingException("Invoice XML missing required ExchangedDocument element");
            }

            SupplyChainTradeTransactionType transaction = jaxbInvoice.getSupplyChainTradeTransaction();
            if (transaction == null) {
                throw new InvoiceParsingException("Invoice XML missing required SupplyChainTradeTransaction element");
            }

            // Step 3: Map to domain model
            LocalDate issueDate = extractIssueDate(document);

            ProcessedInvoice invoice = ProcessedInvoice.builder()
                .id(InvoiceId.generate())
                .sourceInvoiceId(sourceInvoiceId)
                .invoiceNumber(extractInvoiceNumber(document))
                .issueDate(issueDate)
                .dueDate(extractDueDate(transaction, issueDate))
                .seller(extractSeller(transaction))
                .buyer(extractBuyer(transaction))
                .items(extractLineItems(transaction))
                .currency(extractCurrency(transaction))
                .originalXml(xmlContent)
                .build();

            log.info("Successfully parsed invoice {} with {} line items",
                invoice.getInvoiceNumber(), invoice.getItems().size());

            return invoice;

        } catch (InvoiceParsingException e) {
            log.error("Failed to parse invoice XML for source ID {}: {}",
                sourceInvoiceId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error parsing invoice XML for source ID " + sourceInvoiceId, e);
            throw new InvoiceParsingException("Unexpected error during invoice parsing", e);
        }
    }

    /**
     * Unmarshal XML string to JAXB object
     */
    private Invoice_CrossIndustryInvoiceType unmarshalXml(String xmlContent)
            throws InvoiceParsingException {

        if (xmlContent == null || xmlContent.isBlank()) {
            throw new InvoiceParsingException("XML content is null or empty");
        }

        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            StringReader reader = new StringReader(xmlContent);

            Object result = unmarshaller.unmarshal(reader);

            // Handle JAXBElement wrapper (common when no @XmlRootElement annotation)
            if (result instanceof jakarta.xml.bind.JAXBElement) {
                jakarta.xml.bind.JAXBElement<?> jaxbElement = (jakarta.xml.bind.JAXBElement<?>) result;
                result = jaxbElement.getValue();
            }

            if (!(result instanceof Invoice_CrossIndustryInvoiceType)) {
                throw new InvoiceParsingException(
                    "Unexpected root element: " + result.getClass().getName()
                );
            }

            return (Invoice_CrossIndustryInvoiceType) result;

        } catch (JAXBException e) {
            log.error("JAXB unmarshalling failed", e);
            throw new InvoiceParsingException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    /**
     * Extract invoice number from document
     */
    private String extractInvoiceNumber(ExchangedDocumentType document)
            throws InvoiceParsingException {

        if (document.getID() == null || document.getID().getValue() == null) {
            throw new InvoiceParsingException("Invoice number (ID) is missing");
        }

        return document.getID().getValue();
    }

    /**
     * Extract issue date from document
     */
    private LocalDate extractIssueDate(ExchangedDocumentType document)
            throws InvoiceParsingException {

        XMLGregorianCalendar issueDateTime = document.getIssueDateTime();
        if (issueDateTime == null) {
            throw new InvoiceParsingException("Issue date/time is missing");
        }

        return convertXMLGregorianCalendarToLocalDate(issueDateTime);
    }

    /**
     * Extract due date from transaction settlement
     */
    private LocalDate extractDueDate(SupplyChainTradeTransactionType transaction, LocalDate issueDate)
            throws InvoiceParsingException {

        HeaderTradeSettlementType settlement = transaction.getApplicableHeaderTradeSettlement();
        if (settlement == null) {
            throw new InvoiceParsingException("Trade settlement information is missing");
        }

        // Due date might be in payment terms
        List<TradePaymentTermsType> paymentTerms = settlement.getSpecifiedTradePaymentTerms();
        if (paymentTerms != null && !paymentTerms.isEmpty()) {
            TradePaymentTermsType terms = paymentTerms.get(0);
            XMLGregorianCalendar dueDateTime = terms.getDueDateDateTime();
            if (dueDateTime != null) {
                return convertXMLGregorianCalendarToLocalDate(dueDateTime);
            }
        }

        // Default to issue date + 30 days if not specified
        log.warn("Due date not found in XML, defaulting to issue date + 30 days");
        return issueDate.plusDays(30);
    }

    /**
     * Extract seller party information
     */
    private Party extractSeller(SupplyChainTradeTransactionType transaction)
            throws InvoiceParsingException {

        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getSellerTradeParty() == null) {
            throw new InvoiceParsingException("Seller information is missing");
        }

        return mapParty(agreement.getSellerTradeParty(), "Seller");
    }

    /**
     * Extract buyer party information
     */
    private Party extractBuyer(SupplyChainTradeTransactionType transaction)
            throws InvoiceParsingException {

        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getBuyerTradeParty() == null) {
            throw new InvoiceParsingException("Buyer information is missing");
        }

        return mapParty(agreement.getBuyerTradeParty(), "Buyer");
    }

    /**
     * Map JAXB trade party to domain Party
     */
    private Party mapParty(TradePartyType jaxbParty, String partyType)
            throws InvoiceParsingException {

        // Extract name
        String name = Optional.ofNullable(jaxbParty.getName())
            .map(n -> n.getValue())
            .orElseThrow(() -> new InvoiceParsingException(partyType + " name is missing"));

        // Extract tax identifier
        TaxIdentifier taxIdentifier = extractTaxIdentifier(jaxbParty, partyType);

        // Extract address
        Address address = extractAddress(jaxbParty, partyType);

        // Extract email (optional)
        String email = null;
        List<TradeContactType> contacts = jaxbParty.getDefinedTradeContact();
        if (contacts != null && !contacts.isEmpty()) {
            TradeContactType contact = contacts.get(0);
            if (contact.getEmailURIUniversalCommunication() != null &&
                contact.getEmailURIUniversalCommunication().getURIID() != null) {
                email = contact.getEmailURIUniversalCommunication().getURIID().getValue();
            }
        }

        return Party.of(name, taxIdentifier, address, email);
    }

    /**
     * Extract tax identifier from party
     */
    private TaxIdentifier extractTaxIdentifier(TradePartyType jaxbParty, String partyType)
            throws InvoiceParsingException {

        TaxRegistrationType taxReg = jaxbParty.getSpecifiedTaxRegistration();
        if (taxReg == null) {
            throw new InvoiceParsingException(partyType + " tax registration is missing");
        }

        if (taxReg.getID() == null || taxReg.getID().getValue() == null) {
            throw new InvoiceParsingException(partyType + " tax ID is missing");
        }

        String taxId = taxReg.getID().getValue();
        String scheme = Optional.ofNullable(taxReg.getID().getSchemeID())
            .orElse("VAT");

        return TaxIdentifier.of(taxId, scheme);
    }

    /**
     * Extract address from party
     */
    private Address extractAddress(TradePartyType jaxbParty, String partyType)
            throws InvoiceParsingException {

        TradeAddressType jaxbAddress = jaxbParty.getPostalTradeAddress();
        if (jaxbAddress == null) {
            throw new InvoiceParsingException(partyType + " address is missing");
        }

        // Build address (some fields may be optional)
        String streetAddress = Optional.ofNullable(jaxbAddress.getLineOne())
            .map(line -> line.getValue())
            .orElse(null);

        String city = Optional.ofNullable(jaxbAddress.getCityName())
            .map(name -> name.getValue())
            .orElse(null);

        String postalCode = Optional.ofNullable(jaxbAddress.getPostcodeCode())
            .map(code -> code.getValue())
            .orElse(null);

        String country = null;
        if (jaxbAddress.getCountryID() != null && jaxbAddress.getCountryID().getValue() != null) {
            country = jaxbAddress.getCountryID().getValue().value();
        }
        if (country == null) {
            throw new InvoiceParsingException(partyType + " country is missing");
        }

        return Address.of(streetAddress, city, postalCode, country);
    }

    /**
     * Extract line items
     */
    private List<LineItem> extractLineItems(SupplyChainTradeTransactionType transaction)
            throws InvoiceParsingException {

        List<SupplyChainTradeLineItemType> jaxbItems =
            transaction.getIncludedSupplyChainTradeLineItem();

        if (jaxbItems == null || jaxbItems.isEmpty()) {
            throw new InvoiceParsingException("Invoice must have at least one line item");
        }

        List<LineItem> items = new ArrayList<>();
        String currency = extractCurrency(transaction);

        for (int i = 0; i < jaxbItems.size(); i++) {
            try {
                LineItem item = mapLineItem(jaxbItems.get(i), currency);
                items.add(item);
            } catch (Exception e) {
                throw new InvoiceParsingException(
                    "Failed to parse line item " + (i + 1) + ": " + e.getMessage(), e
                );
            }
        }

        return items;
    }

    /**
     * Map JAXB line item to domain LineItem
     */
    private LineItem mapLineItem(SupplyChainTradeLineItemType jaxbItem, String currency)
            throws InvoiceParsingException {

        // Extract product description
        TradeProductType product = jaxbItem.getSpecifiedTradeProduct();
        if (product == null || product.getName() == null || product.getName().isEmpty()) {
            throw new InvoiceParsingException("Line item product name is missing");
        }
        String description = product.getName().get(0).getValue();

        // Extract quantity
        LineTradeDeliveryType delivery = jaxbItem.getSpecifiedLineTradeDelivery();
        if (delivery == null || delivery.getBilledQuantity() == null) {
            throw new InvoiceParsingException("Line item quantity is missing");
        }
        BigDecimal quantityDecimal = delivery.getBilledQuantity().getValue();
        int quantity = quantityDecimal.intValue();

        // Extract unit price
        LineTradeAgreementType agreement = jaxbItem.getSpecifiedLineTradeAgreement();
        if (agreement == null || agreement.getGrossPriceProductTradePrice() == null) {
            throw new InvoiceParsingException("Line item unit price is missing");
        }
        TradePriceType priceType = agreement.getGrossPriceProductTradePrice();
        if (priceType.getChargeAmount() == null || priceType.getChargeAmount().size() == 0) {
            throw new InvoiceParsingException("Line item price amount is missing");
        }
        BigDecimal unitPriceAmount = priceType.getChargeAmount().get(0).getValue();
        Money unitPrice = Money.of(unitPriceAmount, currency);

        // Extract tax rate
        LineTradeSettlementType settlement = jaxbItem.getSpecifiedLineTradeSettlement();
        BigDecimal taxRate = BigDecimal.ZERO;

        if (settlement != null && settlement.getApplicableTradeTax() != null
            && !settlement.getApplicableTradeTax().isEmpty()) {

            TradeTaxType tax = settlement.getApplicableTradeTax().get(0);
            if (tax.getCalculatedRate() != null) {
                taxRate = tax.getCalculatedRate();
            }
        }

        return new LineItem(description, quantity, unitPrice, taxRate);
    }

    /**
     * Extract currency code
     */
    private String extractCurrency(SupplyChainTradeTransactionType transaction)
            throws InvoiceParsingException {

        HeaderTradeSettlementType settlement = transaction.getApplicableHeaderTradeSettlement();
        if (settlement == null || settlement.getInvoiceCurrencyCode() == null) {
            throw new InvoiceParsingException("Invoice currency is missing");
        }

        String currency = null;
        if (settlement.getInvoiceCurrencyCode().getValue() != null) {
            currency = settlement.getInvoiceCurrencyCode().getValue().value();
        }

        if (currency == null || currency.length() != 3) {
            throw new InvoiceParsingException("Invalid currency code: " + currency);
        }

        return currency;
    }

    /**
     * Convert XMLGregorianCalendar to LocalDate
     */
    private LocalDate convertXMLGregorianCalendarToLocalDate(XMLGregorianCalendar calendar) {
        if (calendar == null) {
            return null;
        }
        return LocalDate.of(
            calendar.getYear(),
            calendar.getMonth(),
            calendar.getDay()
        );
    }
}