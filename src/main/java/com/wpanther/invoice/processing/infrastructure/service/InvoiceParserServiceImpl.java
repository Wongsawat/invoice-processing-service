package com.wpanther.invoice.processing.infrastructure.service;

import com.wpanther.invoice.processing.domain.model.Address;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.LineItem;
import com.wpanther.invoice.processing.domain.model.Money;
import com.wpanther.invoice.processing.domain.model.Party;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.TaxIdentifier;
import com.wpanther.invoice.processing.domain.port.out.InvoiceParserPort;
import com.wpanther.etax.generated.invoice.ram.*;
import com.wpanther.etax.generated.invoice.rsm.Invoice_CrossIndustryInvoiceType;
import jakarta.annotation.PreDestroy;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of InvoiceParserPort that uses teda library's JAXB classes
 * to parse Thai e-Tax invoice XML.
 *
 * <p>Two safety guards are applied before JAXB touches the bytes:
 * <ol>
 *   <li><b>Size check</b> — rejects payloads larger than {@value #MAX_XML_BYTES} bytes
 *       (UTF-8) to prevent memory exhaustion from deliberately oversized inputs.</li>
 *   <li><b>Wall-clock timeout</b> — the JAXB unmarshal runs in a dedicated virtual-thread
 *       executor; if parsing has not finished within {@code app.parsing.timeout-seconds}
 *       (default 10 s) the task is cancelled and a {@link InvoiceParserPort.InvoiceParsingException}
 *       is thrown. This prevents a pathological XML structure from blocking a Camel
 *       consumer thread indefinitely even when entity-expansion caps are in place.</li>
 * </ol>
 */
@Slf4j
@Service
public class InvoiceParserServiceImpl implements InvoiceParserPort {

    private static final ZoneId TH_ZONE = ZoneId.of("Asia/Bangkok");

    /** Maximum accepted XML payload size (UTF-8 bytes). */
    static final int MAX_XML_BYTES = 500 * 1024; // 500 KB

    /**
     * Recognised tax identifier scheme codes per Thai e-Tax specification.
     * An unrecognised schemeID is logged and silently replaced with "VAT" rather
     * than rejecting the document, because the scheme is metadata that does not
     * affect tax calculations. A multi-kilobyte scheme string is thereby also
     * sanitised before reaching the database.
     */
    private static final Set<String> VALID_TAX_ID_SCHEMES = Set.of("VAT", "EIN", "TAX");

    private final JAXBContext jaxbContext;
    private final SAXParserFactory saxParserFactory;
    private final long parseTimeoutMs;

    /**
     * Number of days added to the issue date when the XML omits a due date.
     * Configured via {@code app.invoice.default-due-date-days} (default 30).
     */
    private final int defaultDueDateDays;

    /**
     * Dedicated virtual-thread executor for timed JAXB unmarshal operations.
     * Virtual threads (Java 21) are cheap, so each parse call gets its own thread
     * without consuming platform threads from the Camel consumer pool.
     */
    private final ExecutorService parseExecutor;

    /**
     * Caps the number of concurrent JAXB unmarshal tasks.
     * {@code newVirtualThreadPerTaskExecutor} is unbounded by design; without this
     * guard a burst of requests would spawn an unlimited number of CPU-bound virtual
     * threads, all competing for the same platform threads and degrading throughput.
     * Configured via {@code app.parsing.max-concurrent} (default: 300 =
     * consumersCount(3) × maxPollRecords(100) — matches the maximum burst the
     * Camel consumer can deliver, so no artificial backpressure is applied).
     */
    private final Semaphore parseSemaphore;

    // ---- Constructors -------------------------------------------------------

    /**
     * Production constructor — Spring resolves all configurable values and injects them.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public InvoiceParserServiceImpl(
            @Value("${app.parsing.timeout-seconds:10}") int parseTimeoutSeconds,
            @Value("${app.parsing.max-concurrent:300}") int maxConcurrentParses,
            @Value("${app.invoice.default-due-date-days:30}") int defaultDueDateDays) {
        this(TimeUnit.SECONDS.toMillis(parseTimeoutSeconds), defaultDueDateDays, maxConcurrentParses);
    }

    /**
     * Package-private constructor for unit tests that need fine-grained timeout control
     * (e.g. 100 ms instead of 10 s) without a Spring context. Uses the default 30-day
     * due-date fallback and unbounded concurrency.
     */
    InvoiceParserServiceImpl(long timeout, TimeUnit unit) {
        this(unit.toMillis(timeout), 30, Integer.MAX_VALUE);
    }

    /**
     * Package-private constructor for unit tests that need both a custom timeout
     * and a custom due-date default (e.g. to verify the configurable value is used).
     */
    InvoiceParserServiceImpl(long timeout, TimeUnit unit, int defaultDueDateDays) {
        this(unit.toMillis(timeout), defaultDueDateDays, Integer.MAX_VALUE);
    }

    /**
     * Package-private constructor for tests that need to exercise the concurrency cap.
     */
    InvoiceParserServiceImpl(long timeout, TimeUnit unit, int defaultDueDateDays, int maxConcurrentParses) {
        this(unit.toMillis(timeout), defaultDueDateDays, maxConcurrentParses);
    }

    /**
     * No-arg constructor kept for existing test classes that call
     * {@code new InvoiceParserServiceImpl()} directly. Uses production defaults
     * and unbounded concurrency (tests are single-threaded).
     */
    InvoiceParserServiceImpl() {
        this(TimeUnit.SECONDS.toMillis(10), 30, Integer.MAX_VALUE);
    }

    private InvoiceParserServiceImpl(long parseTimeoutMs, int defaultDueDateDays, int maxConcurrentParses) {
        this.parseTimeoutMs = parseTimeoutMs;
        this.defaultDueDateDays = defaultDueDateDays;
        this.parseSemaphore = new Semaphore(maxConcurrentParses);
        this.parseExecutor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            // Initialize JAXB context with the implementation package.
            // The teda library uses interface/implementation pattern with a custom
            // JAXBContextFactory. We use the package path so the factory handles context
            // creation. CRITICAL: must use invoice packages, NOT taxinvoice packages.
            String contextPath = "com.wpanther.etax.generated.invoice.rsm.impl" +
                               ":com.wpanther.etax.generated.invoice.ram.impl" +
                               ":com.wpanther.etax.generated.common.qdt.impl" +
                               ":com.wpanther.etax.generated.common.udt.impl";
            this.jaxbContext = JAXBContext.newInstance(contextPath);
            log.info("JAXB context initialized successfully for Thai e-Tax invoice parsing");
        } catch (JAXBException e) {
            log.error("Failed to initialize JAXB context", e);
            throw new IllegalStateException("Failed to initialize XML parser", e);
        }

        try {
            // Secure SAX parser factory — disables external entities and DOCTYPE to
            // prevent XXE / XML-bomb attacks.
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            this.saxParserFactory = factory;
        } catch (ParserConfigurationException | org.xml.sax.SAXException e) {
            throw new IllegalStateException("Failed to initialize secure SAX parser factory", e);
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        parseExecutor.shutdown();
    }

    // ---- Public API ---------------------------------------------------------

    @Override
    public ProcessedInvoice parse(String xmlContent, String sourceInvoiceId)
            throws InvoiceParsingException {

        log.debug("Starting XML parsing for source invoice ID: {}", sourceInvoiceId);

        try {
            // Step 1: Unmarshal XML to JAXB object (size-checked and time-bounded)
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
            String currency = extractCurrency(transaction);

            ProcessedInvoice invoice = ProcessedInvoice.builder()
                .id(InvoiceId.generate())
                .sourceInvoiceId(sourceInvoiceId)
                .invoiceNumber(extractInvoiceNumber(document))
                .issueDate(issueDate)
                .dueDate(extractDueDate(transaction, issueDate))
                .seller(extractSeller(transaction))
                .buyer(extractBuyer(transaction))
                .items(extractLineItems(transaction, currency))
                .currency(currency)
                .originalXml(xmlContent)
                .build();

            log.info("Successfully parsed invoice {} with {} line items",
                invoice.getInvoiceNumber(), invoice.getItems().size());

            return invoice;

        } catch (InvoiceParsingException e) {
            log.error("Failed to parse invoice XML for source ID {}: {}",
                sourceInvoiceId, e.getMessage());
            throw e;
        }
    }

    // ---- Unmarshal (size-check + timeout) -----------------------------------

    /**
     * Validates the XML payload size and delegates the actual JAXB unmarshal to
     * {@link #doUnmarshal(String)}, which runs inside a time-bounded executor task.
     *
     * <p>If the parse does not complete within {@code parseTimeoutMs} milliseconds
     * the executor task is cancelled and a {@link InvoiceParserPort.InvoiceParsingException}
     * is thrown, preventing indefinite blocking of the calling Camel consumer thread.
     */
    private Invoice_CrossIndustryInvoiceType unmarshalXml(String xmlContent)
            throws InvoiceParsingException {

        if (xmlContent == null || xmlContent.isBlank()) {
            throw InvoiceParsingException.forEmpty();
        }

        // Reject oversized payloads before touching the SAX parser.
        int byteSize = xmlContent.getBytes(StandardCharsets.UTF_8).length;
        if (byteSize > MAX_XML_BYTES) {
            throw InvoiceParsingException.forOversized(byteSize, MAX_XML_BYTES);
        }

        // Apply the concurrency cap before spawning a virtual thread.
        // Without this guard, newVirtualThreadPerTaskExecutor() is unbounded and a
        // burst of requests would create unlimited CPU-bound virtual threads, all
        // competing for the same platform thread pool and degrading throughput.
        try {
            parseSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw InvoiceParsingException.forInterrupted();
        }

        Future<Invoice_CrossIndustryInvoiceType> future =
            parseExecutor.submit(() -> doUnmarshal(xmlContent));

        try {
            return future.get(parseTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw InvoiceParsingException.forTimeout(parseTimeoutMs);
        } catch (ExecutionException e) {
            future.cancel(true);
            Throwable cause = e.getCause();
            if (cause instanceof InvoiceParsingException ex) {
                throw ex;
            }
            throw InvoiceParsingException.forUnmarshal(cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw InvoiceParsingException.forInterrupted();
        } finally {
            parseSemaphore.release();
        }
    }

    /**
     * Performs the actual JAXB unmarshal. Runs inside an executor task submitted
     * by {@link #unmarshalXml(String)} so that it can be cancelled if it takes too long.
     */
    private Invoice_CrossIndustryInvoiceType doUnmarshal(String xmlContent)
            throws InvoiceParsingException {

        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            Object result;
            try (StringReader reader = new StringReader(xmlContent)) {
                org.xml.sax.XMLReader xmlReader = saxParserFactory.newSAXParser().getXMLReader();
                // Defense-in-depth: explicitly re-apply FEATURE_SECURE_PROCESSING on the
                // XMLReader at point of use, not just on the factory. This caps entity
                // expansion (JDK limits to 64,000 expansions) as a second guard against
                // billion-laughs attacks in case the factory-level feature was not fully
                // propagated by the underlying parser implementation.
                xmlReader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                SAXSource saxSource = new SAXSource(xmlReader, new InputSource(reader));
                result = unmarshaller.unmarshal(saxSource);
            }

            // Handle JAXBElement wrapper (common when no @XmlRootElement annotation)
            if (result instanceof jakarta.xml.bind.JAXBElement) {
                result = ((jakarta.xml.bind.JAXBElement<?>) result).getValue();
            }

            if (!(result instanceof Invoice_CrossIndustryInvoiceType)) {
                throw InvoiceParsingException.forUnexpectedRootElement(result.getClass().getName());
            }

            return (Invoice_CrossIndustryInvoiceType) result;

        } catch (JAXBException | org.xml.sax.SAXException | ParserConfigurationException e) {
            log.error("JAXB unmarshalling failed", e);
            throw InvoiceParsingException.forUnmarshal(e);
        }
    }

    // ---- Domain extraction helpers ------------------------------------------

    private String extractInvoiceNumber(ExchangedDocumentType document)
            throws InvoiceParsingException {

        if (document.getID() == null || document.getID().getValue() == null) {
            throw new InvoiceParsingException("Invoice number (ID) is missing");
        }
        return document.getID().getValue();
    }

    private LocalDate extractIssueDate(ExchangedDocumentType document)
            throws InvoiceParsingException {

        XMLGregorianCalendar issueDateTime = document.getIssueDateTime();
        if (issueDateTime == null) {
            throw new InvoiceParsingException("Issue date/time is missing");
        }
        return convertXMLGregorianCalendarToLocalDate(issueDateTime);
    }

    private LocalDate extractDueDate(SupplyChainTradeTransactionType transaction, LocalDate issueDate)
            throws InvoiceParsingException {

        HeaderTradeSettlementType settlement = transaction.getApplicableHeaderTradeSettlement();

        List<TradePaymentTermsType> paymentTerms = settlement.getSpecifiedTradePaymentTerms();
        if (paymentTerms != null && !paymentTerms.isEmpty()) {
            XMLGregorianCalendar dueDateTime = paymentTerms.get(0).getDueDateDateTime();
            if (dueDateTime != null) {
                return convertXMLGregorianCalendarToLocalDate(dueDateTime);
            }
        }

        // Default when XML omits due date (configurable via app.invoice.default-due-date-days)
        log.warn("Due date not found in XML, defaulting to issue date + {} days", defaultDueDateDays);
        return issueDate.plusDays(defaultDueDateDays);
    }

    private Party extractSeller(SupplyChainTradeTransactionType transaction)
            throws InvoiceParsingException {

        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getSellerTradeParty() == null) {
            throw new InvoiceParsingException("Seller information is missing");
        }
        return mapParty(agreement.getSellerTradeParty(), "Seller");
    }

    private Party extractBuyer(SupplyChainTradeTransactionType transaction)
            throws InvoiceParsingException {

        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getBuyerTradeParty() == null) {
            throw new InvoiceParsingException("Buyer information is missing");
        }
        return mapParty(agreement.getBuyerTradeParty(), "Buyer");
    }

    private Party mapParty(TradePartyType jaxbParty, String partyType)
            throws InvoiceParsingException {

        String name = Optional.ofNullable(jaxbParty.getName())
            .map(n -> n.getValue())
            .orElseThrow(() -> new InvoiceParsingException(partyType + " name is missing"));

        TaxIdentifier taxIdentifier = extractTaxIdentifier(jaxbParty, partyType);
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
        String scheme = Optional.ofNullable(taxReg.getID().getSchemeID()).orElse("VAT");
        if (!VALID_TAX_ID_SCHEMES.contains(scheme)) {
            log.warn("{} tax identifier scheme '{}' is not recognised — defaulting to VAT",
                    partyType, scheme);
            scheme = "VAT";
        }
        return TaxIdentifier.of(taxId, scheme);
    }

    /**
     * Extract address from party.
     *
     * <p>Returns {@code null} when {@code PostalTradeAddress} is absent or its
     * {@code CountryID} is missing — both are permitted by the Thai e-Tax XSD.
     * Callers receive a null {@link Address} stored on the {@link Party}, which
     * the persistence mapper also handles (country column is nullable).
     */
    private Address extractAddress(TradePartyType jaxbParty, String partyType) {

        TradeAddressType jaxbAddress = jaxbParty.getPostalTradeAddress();
        if (jaxbAddress == null) {
            log.warn("{} PostalTradeAddress element is absent — party address will be null "
                + "(optional per Thai e-Tax XSD)", partyType);
            return null;
        }

        String streetAddress = Optional.ofNullable(jaxbAddress.getLineOne())
            .map(line -> line.getValue()).orElse(null);
        String city = Optional.ofNullable(jaxbAddress.getCityName())
            .map(name -> name.getValue()).orElse(null);
        String postalCode = Optional.ofNullable(jaxbAddress.getPostcodeCode())
            .map(code -> code.getValue()).orElse(null);

        String country = null;
        if (jaxbAddress.getCountryID() != null && jaxbAddress.getCountryID().getValue() != null) {
            country = jaxbAddress.getCountryID().getValue().value();
        }
        if (country == null) {
            log.warn("{} PostalTradeAddress present but CountryID is absent — party address will be null "
                + "(optional per Thai e-Tax XSD). Discarded: street='{}', city='{}', postalCode='{}'",
                partyType, streetAddress, city, postalCode);
            return null;
        }

        return Address.of(streetAddress, city, postalCode, country);
    }

    private List<LineItem> extractLineItems(SupplyChainTradeTransactionType transaction, String currency)
            throws InvoiceParsingException {

        List<SupplyChainTradeLineItemType> jaxbItems =
            transaction.getIncludedSupplyChainTradeLineItem();

        if (jaxbItems == null || jaxbItems.isEmpty()) {
            throw new InvoiceParsingException("Invoice must have at least one line item");
        }

        List<LineItem> items = new ArrayList<>();
        for (SupplyChainTradeLineItemType jaxbItem : jaxbItems) {
            items.add(mapLineItem(jaxbItem, currency));
        }
        return items;
    }

    private LineItem mapLineItem(SupplyChainTradeLineItemType jaxbItem, String currency)
            throws InvoiceParsingException {

        // Product description
        TradeProductType product = jaxbItem.getSpecifiedTradeProduct();
        if (product == null || product.getName() == null || product.getName().isEmpty()) {
            throw new InvoiceParsingException("Line item product name is missing");
        }
        String description = product.getName().get(0).getValue();

        // Quantity
        LineTradeDeliveryType delivery = jaxbItem.getSpecifiedLineTradeDelivery();
        if (delivery == null || delivery.getBilledQuantity() == null) {
            throw new InvoiceParsingException("Line item quantity is missing");
        }
        BigDecimal quantityDecimal = delivery.getBilledQuantity().getValue();
        if (quantityDecimal.stripTrailingZeros().scale() > 0) {
            throw new InvoiceParsingException(
                "Line item quantity must be a whole number, got: " + quantityDecimal);
        }
        int quantity;
        try {
            quantity = quantityDecimal.intValueExact();
        } catch (ArithmeticException e) {
            throw new InvoiceParsingException(
                "Line item quantity out of integer range: " + quantityDecimal, e);
        }

        // Unit price
        LineTradeAgreementType agreement = jaxbItem.getSpecifiedLineTradeAgreement();
        if (agreement == null || agreement.getGrossPriceProductTradePrice() == null) {
            throw new InvoiceParsingException("Line item unit price is missing");
        }
        TradePriceType priceType = agreement.getGrossPriceProductTradePrice();
        if (priceType.getChargeAmount() == null || priceType.getChargeAmount().isEmpty()) {
            throw new InvoiceParsingException("Line item price amount is missing");
        }
        if (priceType.getChargeAmount().size() > 1) {
            throw new InvoiceParsingException(
                "Line item has " + priceType.getChargeAmount().size() + " price amounts; "
                + "exactly one ChargeAmount is expected per GrossPriceProductTradePrice");
        }
        Money unitPrice = Money.of(priceType.getChargeAmount().get(0).getValue(), currency);

        // Tax rate (optional — defaults to 0%)
        LineTradeSettlementType settlement = jaxbItem.getSpecifiedLineTradeSettlement();
        BigDecimal taxRate = BigDecimal.ZERO;
        if (settlement != null && settlement.getApplicableTradeTax() != null
                && !settlement.getApplicableTradeTax().isEmpty()) {
            TradeTaxType tax = settlement.getApplicableTradeTax().get(0);
            if (tax.getCalculatedRate() != null) {
                taxRate = tax.getCalculatedRate();
                if (taxRate.compareTo(BigDecimal.ZERO) < 0
                        || taxRate.compareTo(new BigDecimal("100")) > 0) {
                    throw new InvoiceParsingException(
                        "Tax rate " + taxRate + " is outside valid range [0, 100]");
                }
            }
        }

        return new LineItem(description, quantity, unitPrice, taxRate);
    }

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
     * Convert XMLGregorianCalendar to LocalDate using Thai timezone (UTC+7).
     * When the XML omits the timezone offset (DatatypeConstants.FIELD_UNDEFINED),
     * the timestamp is interpreted as Asia/Bangkok time — matching the Thai e-Tax standard.
     * Without this, a JVM running at UTC would misparse a late-evening Thai datetime
     * (e.g. 2025-01-01T23:30:00 without offset) as the previous calendar day.
     */
    private LocalDate convertXMLGregorianCalendarToLocalDate(XMLGregorianCalendar calendar) {
        ZoneId zone = (calendar.getTimezone() == DatatypeConstants.FIELD_UNDEFINED)
            ? TH_ZONE
            : calendar.toGregorianCalendar().getTimeZone().toZoneId();
        return calendar.toGregorianCalendar().toZonedDateTime()
            .withZoneSameInstant(zone)
            .toLocalDate();
    }
}
