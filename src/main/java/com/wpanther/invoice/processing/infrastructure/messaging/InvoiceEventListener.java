package com.wpanther.invoice.processing.infrastructure.messaging;

import com.wpanther.invoice.processing.application.service.InvoiceProcessingService;
import com.wpanther.invoice.processing.domain.event.InvoiceReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for invoice events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceEventListener {

    private final InvoiceProcessingService processingService;

    /**
     * Listen for invoice received events from intake service
     */
    @KafkaListener(
        topics = "${app.kafka.topics.document-received-invoice}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInvoiceReceived(
        @Payload InvoiceReceivedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        log.info("Received invoice event: {} (partition: {}, offset: {})",
            event.getInvoiceNumber(), partition, offset);

        try {
            // Process the invoice
            processingService.processInvoiceReceived(event);

            // Acknowledge message
            acknowledgment.acknowledge();
            log.debug("Acknowledged invoice received event: {}", event.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Error processing invoice received event: {}", event.getInvoiceNumber(), e);
            // Don't acknowledge - message will be retried
            // In production, implement DLQ after max retries
        }
    }
}
