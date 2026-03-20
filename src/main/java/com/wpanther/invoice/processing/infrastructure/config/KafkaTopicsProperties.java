package com.wpanther.invoice.processing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for all {@code app.kafka.topics.*} configuration properties.
 *
 * <p>Centralises the property keys in one place so that a mistyped key fails at
 * startup (Spring Boot throws {@code BindException} for unresolvable fields) rather
 * than silently routing events to the wrong topic at runtime.
 *
 * <p>YAML key mapping (Spring relaxed binding):
 * <pre>
 *   app.kafka.topics.invoice-processed          → invoiceProcessed
 *   app.kafka.topics.dlq                        → dlq
 *   app.kafka.topics.saga-command-invoice       → sagaCommandInvoice
 *   app.kafka.topics.saga-compensation-invoice  → sagaCompensationInvoice
 *   app.kafka.topics.saga-reply-invoice         → sagaReplyInvoice
 * </pre>
 */
@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicsProperties(
        String invoiceProcessed,
        String dlq,
        String sagaCommandInvoice,
        String sagaCompensationInvoice,
        String sagaReplyInvoice) {
}
