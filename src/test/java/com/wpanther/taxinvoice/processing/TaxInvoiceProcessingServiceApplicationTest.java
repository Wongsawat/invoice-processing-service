package com.wpanther.taxinvoice.processing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaxInvoiceProcessingServiceApplication
 */
@SpringBootTest
@ActiveProfiles("test")
class TaxInvoiceProcessingServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Then
        assertNotNull(applicationContext, "Application context should load successfully");
    }

    @Test
    void testApplicationHasRequiredBeans() {
        // Then
        assertTrue(applicationContext.containsBean("taxInvoiceProcessingService"),
            "Should have TaxInvoiceProcessingService bean");
        assertTrue(applicationContext.containsBean("eventPublisher"),
            "Should have EventPublisher bean");
        assertTrue(applicationContext.containsBean("sagaRouteConfig"),
            "Should have SagaRouteConfig bean");
        assertTrue(applicationContext.containsBean("sagaCommandHandler"),
            "Should have SagaCommandHandler bean");
        assertTrue(applicationContext.containsBean("sagaReplyPublisher"),
            "Should have SagaReplyPublisher bean");
        assertTrue(applicationContext.containsBean("outboxService"),
            "Should have OutboxService bean from saga-commons");
    }

    @Test
    void testApplicationClassAnnotations() {
        // Then - verify the main application class has correct annotations
        assertNotNull(TaxInvoiceProcessingServiceApplication.class.getAnnotation(SpringBootApplication.class),
            "Application class should have @SpringBootApplication annotation");
        assertNotNull(TaxInvoiceProcessingServiceApplication.class.getAnnotation(EnableDiscoveryClient.class),
            "Application class should have @EnableDiscoveryClient annotation");
    }
}
