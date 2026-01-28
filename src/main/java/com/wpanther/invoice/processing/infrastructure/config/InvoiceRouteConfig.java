package com.wpanther.invoice.processing.infrastructure.config;

import com.wpanther.invoice.processing.application.service.InvoiceProcessingService;
import com.wpanther.invoice.processing.domain.event.InvoiceReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for invoice processing.
 * Replaces Spring Kafka consumer and producer configuration.
 */
@Component
@Slf4j
public class InvoiceRouteConfig extends RouteBuilder {

    private final InvoiceProcessingService processingService;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.document-received-invoice}")
    private String inputTopic;

    @Value("${app.kafka.topics.invoice-processed}")
    private String invoiceProcessedTopic;

    @Value("${app.kafka.topics.xml-signing-requested}")
    private String xmlSigningRequestedTopic;

    @Value("${app.kafka.topics.pdf-generation-requested}")
    private String pdfGenerationRequestedTopic;

    @Value("${app.kafka.topics.dlq:invoice.processing.dlq}")
    private String dlqTopic;

    public InvoiceRouteConfig(InvoiceProcessingService processingService) {
        this.processingService = processingService;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler - Dead Letter Channel with retries
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(10000)
            .logExhausted(true)
            .logStackTrace(true));

        // ============================================================
        // CONSUMER ROUTE: document.received.invoice
        // ============================================================
        from("kafka:" + inputTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=invoice-processing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=100"
                + "&consumersCount=3")
            .routeId("invoice-processing-consumer")
            .log("Received invoice from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")

            // Unmarshal JSON to InvoiceReceivedEvent
            .unmarshal().json(JsonLibrary.Jackson, InvoiceReceivedEvent.class)

            // Process the event - call application service
            .process(exchange -> {
                InvoiceReceivedEvent event = exchange.getIn().getBody(InvoiceReceivedEvent.class);
                log.info("Processing invoice: {}", event.getInvoiceNumber());

                // Call existing application service (unchanged)
                processingService.processInvoiceReceived(event);
            })

            .log("Successfully processed invoice");

        // ============================================================
        // PRODUCER ROUTE: invoice.processed
        // ============================================================
        from("direct:publish-invoice-processed")
            .routeId("invoice-processed-producer")
            .log("Publishing InvoiceProcessedEvent: ${body.invoiceNumber}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + invoiceProcessedTopic
                + "?brokers=" + kafkaBrokers
                + "&key=${header.kafka.KEY}")
            .log("Published InvoiceProcessedEvent to " + invoiceProcessedTopic);

        // ============================================================
        // PRODUCER ROUTE: xml.signing.requested
        // ============================================================
        from("direct:publish-xml-signing-requested")
            .routeId("xml-signing-requested-producer")
            .log("Publishing XmlSigningRequestedEvent: ${body.invoiceNumber}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + xmlSigningRequestedTopic
                + "?brokers=" + kafkaBrokers
                + "&key=${header.kafka.KEY}")
            .log("Published XmlSigningRequestedEvent to " + xmlSigningRequestedTopic);

        // ============================================================
        // PRODUCER ROUTE: pdf.generation.requested
        // ============================================================
        from("direct:publish-pdf-generation-requested")
            .routeId("pdf-generation-requested-producer")
            .log("Publishing PdfGenerationRequestedEvent: ${body.invoiceNumber}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + pdfGenerationRequestedTopic
                + "?brokers=" + kafkaBrokers
                + "&key=${header.kafka.KEY}")
            .log("Published PdfGenerationRequestedEvent to " + pdfGenerationRequestedTopic);
    }
}
