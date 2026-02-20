package com.wpanther.taxinvoice.processing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.taxinvoice.processing.application.service.SagaCommandHandler;
import com.wpanther.taxinvoice.processing.domain.event.CompensateTaxInvoiceCommand;
import com.wpanther.taxinvoice.processing.domain.event.ProcessTaxInvoiceCommand;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@CamelSpringBootTest
@SpringBootTest
@ActiveProfiles("test")
@UseAdviceWith
class SagaRouteConfigTest {

    @Autowired
    private CamelContext camelContext;

    @MockBean
    private SagaCommandHandler sagaCommandHandler;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Replace Kafka endpoints with direct endpoints for testing
        AdviceWith.adviceWith(camelContext, "saga-command-consumer", a -> {
            a.replaceFromWith("direct:saga-command");
        });
        AdviceWith.adviceWith(camelContext, "saga-compensation-consumer", a -> {
            a.replaceFromWith("direct:saga-compensation");
        });

        camelContext.start();
    }

    @Test
    void shouldHaveAllRoutesConfigured() {
        List<Route> routes = camelContext.getRoutes();
        assertFalse(routes.isEmpty(), "No Camel routes found");

        List<String> routeIds = routes.stream()
            .map(Route::getRouteId)
            .toList();

        assertTrue(routeIds.contains("saga-command-consumer"),
            "Missing saga-command-consumer route. Found: " + routeIds);
        assertTrue(routeIds.contains("saga-compensation-consumer"),
            "Missing saga-compensation-consumer route. Found: " + routeIds);
    }

    @Test
    void shouldHaveExactlyTwoRoutes() {
        List<Route> routes = camelContext.getRoutes();
        assertEquals(2, routes.size(),
            "Expected exactly 2 routes but found: " + routes.stream()
                .map(Route::getRouteId).toList());
    }

    @Test
    void shouldProcessSagaCommand() throws Exception {
        // Given
        ProcessTaxInvoiceCommand command = new ProcessTaxInvoiceCommand(
            "saga-1", "process-tax-invoice", "corr-1",
            "doc-1", "<xml>test</xml>", "TV-001"
        );
        String json = objectMapper.writeValueAsString(command);

        // When
        ProducerTemplate producer = camelContext.createProducerTemplate();
        producer.sendBody("direct:saga-command", json);

        // Then
        verify(sagaCommandHandler).handleProcessCommand(any(ProcessTaxInvoiceCommand.class));
    }

    @Test
    void shouldProcessCompensationCommand() throws Exception {
        // Given
        CompensateTaxInvoiceCommand command = new CompensateTaxInvoiceCommand(
            "saga-1", "COMPENSATE_process-tax-invoice", "corr-1",
            "process-tax-invoice", "doc-1", "tax-invoice"
        );
        String json = objectMapper.writeValueAsString(command);

        // When
        ProducerTemplate producer = camelContext.createProducerTemplate();
        producer.sendBody("direct:saga-compensation", json);

        // Then
        verify(sagaCommandHandler).handleCompensation(any(CompensateTaxInvoiceCommand.class));
    }
}
