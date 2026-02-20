package com.wpanther.invoice.processing;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

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
        assertNotNull(applicationContext, "Application context should load successfully");
    }

    @Test
    void testApplicationHasRequiredBeans() {
        assertTrue(applicationContext.containsBean("invoiceProcessingService"),
            "Should have InvoiceProcessingService bean");
        assertTrue(applicationContext.containsBean("eventPublisher"),
            "Should have EventPublisher bean");
        assertTrue(applicationContext.containsBean("invoiceRouteConfig"),
            "Should have InvoiceRouteConfig bean");
        assertTrue(applicationContext.containsBean("producerTemplate"),
            "Should have ProducerTemplate bean");
    }

    @Test
    void testApplicationClassHasRequiredAnnotations() {
        assertTrue(InvoiceProcessingServiceApplication.class
            .isAnnotationPresent(SpringBootApplication.class));
        assertTrue(InvoiceProcessingServiceApplication.class
            .isAnnotationPresent(EnableDiscoveryClient.class));
        assertTrue(InvoiceProcessingServiceApplication.class
            .isAnnotationPresent(EnableTransactionManagement.class));
    }

    @Test
    void testMainMethodInvokesSpringApplicationRun() {
        try (MockedStatic<SpringApplication> springApp = mockStatic(SpringApplication.class)) {
            springApp.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                .thenReturn(null);

            InvoiceProcessingServiceApplication.main(new String[]{});

            springApp.verify(() ->
                SpringApplication.run(InvoiceProcessingServiceApplication.class, new String[]{}));
        }
    }
}
