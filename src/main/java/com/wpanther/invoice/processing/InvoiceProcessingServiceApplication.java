package com.wpanther.invoice.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.wpanther.invoice.processing.infrastructure.config.KafkaTopicsProperties;

/**
 * Invoice Processing Service - Main Application
 *
 * This microservice processes validated invoices, enriches data,
 * calculates totals, and publishes notification events.
 *
 * Key Features:
 * - Consumes saga commands from orchestrator via Apache Camel
 * - Parses XML invoices using teda library (Invoice_CrossIndustryInvoice)
 * - Applies business logic and calculations
 * - Publishes InvoiceProcessedEvent for notification service
 * - Uses outbox pattern with Debezium CDC for reliable event delivery
 *
 * @author wpanther
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
@EnableScheduling
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class InvoiceProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceProcessingServiceApplication.class, args);
    }
}
