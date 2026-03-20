package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;

import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.config.KafkaTopicsProperties;
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
public class SagaRouteConfig extends RouteBuilder {

    private static final String GROUP_ID = "invoice-processing-service";

    private final SagaCommandHandler sagaCommandHandler;
    private final KafkaTopicsProperties topics;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.camel.retry.max-redeliveries:3}")
    private int maxRedeliveries;

    @Value("${app.camel.retry.redelivery-delay-ms:1000}")
    private long redeliveryDelayMs;

    @Value("${app.camel.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${app.camel.retry.max-redelivery-delay-ms:10000}")
    private long maxRedeliveryDelayMs;

    @Value("${app.kafka.consumers.max-poll-records:100}")
    private int maxPollRecords;

    @Value("${app.kafka.consumers.count:3}")
    private int consumersCount;

    public SagaRouteConfig(SagaCommandHandler sagaCommandHandler, KafkaTopicsProperties topics) {
        this.sagaCommandHandler = sagaCommandHandler;
        this.topics = topics;
    }

    /**
     * Build common Kafka consumer parameters.
     */
    private String kafkaConsumerParams() {
        return "?brokers=RAW(" + kafkaBrokers + ")"
                + "&groupId=" + GROUP_ID
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler - Dead Letter Channel with retries
        errorHandler(deadLetterChannel("kafka:" + topics.dlq() + "?brokers=RAW(" + kafkaBrokers + ")")
            .maximumRedeliveries(maxRedeliveries)
            .redeliveryDelay(redeliveryDelayMs)
            .useExponentialBackOff()
            .backOffMultiplier(backoffMultiplier)
            .maximumRedeliveryDelay(maxRedeliveryDelayMs)
            .logExhausted(true)
            .logStackTrace(true));

        // ============================================================
        // CONSUMER ROUTE: saga.command.invoice (from orchestrator)
        // ============================================================
        from("kafka:" + topics.sagaCommandInvoice() + kafkaConsumerParams())
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
        from("kafka:" + topics.sagaCompensationInvoice() + kafkaConsumerParams())
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
