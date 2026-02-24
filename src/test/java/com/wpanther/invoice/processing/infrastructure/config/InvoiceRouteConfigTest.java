package com.wpanther.invoice.processing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.processing.application.service.SagaCommandHandler;
import com.wpanther.invoice.processing.domain.event.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.domain.event.ProcessInvoiceCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for InvoiceRouteConfig Camel routes.
 * Uses AdviceWith to replace Kafka endpoints with direct: endpoints for in-process testing.
 */
@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvoiceRouteConfigTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private SagaCommandHandler sagaCommandHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private boolean contextStarted = false;

    @BeforeEach
    void setUpRoutes() throws Exception {
        if (!contextStarted) {
            AdviceWith.adviceWith(camelContext, "saga-command-consumer",
                rb -> rb.replaceFromWith("direct:test-saga-command"));
            AdviceWith.adviceWith(camelContext, "saga-compensation-consumer",
                rb -> rb.replaceFromWith("direct:test-saga-compensation"));
            camelContext.start();
            contextStarted = true;
        }
    }

    @Test
    void sagaCommandConsumerRoute_isRegistered() {
        assertNotNull(camelContext.getRoute("saga-command-consumer"),
            "saga-command-consumer route should be registered");
    }

    @Test
    void sagaCompensationConsumerRoute_isRegistered() {
        assertNotNull(camelContext.getRoute("saga-compensation-consumer"),
            "saga-compensation-consumer route should be registered");
    }

    @Test
    void sagaCommandRoute_deserializesJsonAndCallsHandler() throws Exception {
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-1", SagaStep.PROCESS_INVOICE, "corr-1", "doc-001", "<xml/>", "INV-001"
        );
        String json = objectMapper.writeValueAsString(command);

        producerTemplate.sendBody("direct:test-saga-command", json);

        verify(sagaCommandHandler, timeout(5000).atLeastOnce())
            .handleProcessCommand(any(ProcessInvoiceCommand.class));
    }

    @Test
    void sagaCompensationRoute_deserializesJsonAndCallsHandler() throws Exception {
        CompensateInvoiceCommand command = new CompensateInvoiceCommand(
            "saga-2", SagaStep.PROCESS_INVOICE, "corr-2",
            "process-invoice", "doc-002", "invoice"
        );
        String json = objectMapper.writeValueAsString(command);

        producerTemplate.sendBody("direct:test-saga-compensation", json);

        verify(sagaCommandHandler, timeout(5000).atLeastOnce())
            .handleCompensation(any(CompensateInvoiceCommand.class));
    }

    @Test
    void sagaCommandRoute_whenHandlerThrows_routeHandlesException() throws Exception {
        doThrow(new RuntimeException("Processing error"))
            .when(sagaCommandHandler).handleProcessCommand(any());

        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            "saga-err", SagaStep.PROCESS_INVOICE, "corr-err", "doc-err", "<xml/>", "INV-ERR"
        );
        String json = objectMapper.writeValueAsString(command);

        // Route has dead letter channel - exception should be handled, not propagated
        producerTemplate.sendBody("direct:test-saga-command", json);

        verify(sagaCommandHandler, timeout(5000).atLeastOnce())
            .handleProcessCommand(any(ProcessInvoiceCommand.class));
    }
}
