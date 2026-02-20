package com.wpanther.invoice.processing.infrastructure.config;

import com.wpanther.invoice.processing.application.service.SagaCommandHandler;
import com.wpanther.invoice.processing.domain.event.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.domain.event.ProcessInvoiceCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for saga command and compensation consumers.
 */
@Component
@Slf4j
public class InvoiceRouteConfig extends RouteBuilder {

    private static final int MAX_REDELIVERIES = 3;
    private static final long REDELIVERY_DELAY_MS = 1000;
    private static final double BACKOFF_MULTIPLIER = 2;
    private static final long MAX_REDELIVERY_DELAY_MS = 10000;
    private static final int MAX_POLL_RECORDS = 100;
    private static final int CONSUMERS_COUNT = 3;
    private static final String GROUP_ID = "invoice-processing-service";

    private final SagaCommandHandler sagaCommandHandler;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command-invoice}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation-invoice}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.dlq:invoice.processing.dlq}")
    private String dlqTopic;

    public InvoiceRouteConfig(SagaCommandHandler sagaCommandHandler) {
        this.sagaCommandHandler = sagaCommandHandler;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler - Dead Letter Channel with retries
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=RAW(" + kafkaBrokers + ")")
            .maximumRedeliveries(MAX_REDELIVERIES)
            .redeliveryDelay(REDELIVERY_DELAY_MS)
            .useExponentialBackOff()
            .backOffMultiplier(BACKOFF_MULTIPLIER)
            .maximumRedeliveryDelay(MAX_REDELIVERY_DELAY_MS)
            .logExhausted(true)
            .logStackTrace(true));

        // ============================================================
        // CONSUMER ROUTE: saga.command.invoice (from orchestrator)
        // ============================================================
        from("kafka:" + sagaCommandTopic
                + "?brokers=RAW(" + kafkaBrokers + ")"
                + "&groupId=" + GROUP_ID
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=" + MAX_POLL_RECORDS
                + "&consumersCount=" + CONSUMERS_COUNT)
            .routeId("saga-command-consumer")
            .log("Received saga command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
            .unmarshal().json(JsonLibrary.Jackson, ProcessInvoiceCommand.class)
            .process(exchange -> {
                ProcessInvoiceCommand cmd = exchange.getIn().getBody(ProcessInvoiceCommand.class);
                log.info("Processing saga command for saga: {}, invoice: {}",
                    cmd.getSagaId(), cmd.getInvoiceNumber());
                sagaCommandHandler.handleProcessCommand(cmd);
            })
            .log("Successfully processed saga command");

        // ============================================================
        // CONSUMER ROUTE: saga.compensation.invoice (from orchestrator)
        // ============================================================
        from("kafka:" + sagaCompensationTopic
                + "?brokers=RAW(" + kafkaBrokers + ")"
                + "&groupId=" + GROUP_ID
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=" + MAX_POLL_RECORDS
                + "&consumersCount=" + CONSUMERS_COUNT)
            .routeId("saga-compensation-consumer")
            .log("Received compensation command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
            .unmarshal().json(JsonLibrary.Jackson, CompensateInvoiceCommand.class)
            .process(exchange -> {
                CompensateInvoiceCommand cmd = exchange.getIn().getBody(CompensateInvoiceCommand.class);
                log.info("Processing compensation for saga: {}, document: {}",
                    cmd.getSagaId(), cmd.getDocumentId());
                sagaCommandHandler.handleCompensation(cmd);
            })
            .log("Successfully processed compensation command");
    }
}
