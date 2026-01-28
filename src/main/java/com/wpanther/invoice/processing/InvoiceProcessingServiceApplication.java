package com.wpanther.invoice.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Invoice Processing Service - Main Application
 *
 * This microservice processes validated invoices, enriches data,
 * calculates totals, and requests PDF generation.
 *
 * Key Features:
 * - Consumes InvoiceReceivedEvent from Kafka via Apache Camel
 * - Parses XML invoices using teda library
 * - Applies business logic and calculations
 * - Publishes InvoiceProcessedEvent
 * - Requests PDF generation via events
 *
 * @author wpanther
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
public class InvoiceProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceProcessingServiceApplication.class, args);
    }
}
