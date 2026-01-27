package com.wpanther.invoice.processing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InvoiceProcessingServiceApplication
 */
@SpringBootTest
@ActiveProfiles("test")
class InvoiceProcessingServiceApplicationTest {

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
        assertTrue(applicationContext.containsBean("invoiceProcessingService"),
            "Should have InvoiceProcessingService bean");
        assertTrue(applicationContext.containsBean("eventPublisher"),
            "Should have EventPublisher bean");
        assertTrue(applicationContext.containsBean("invoiceEventListener"),
            "Should have InvoiceEventListener bean");
    }
}
