package com.wpanther.invoice.processing.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaReplyPublisherTest {

    @Mock
    private OutboxService outboxService;

    private SagaReplyPublisher sagaReplyPublisher;

    @BeforeEach
    void setUp() {
        HeaderSerializer headerSerializer = new HeaderSerializer(new ObjectMapper());
        sagaReplyPublisher = new SagaReplyPublisher(outboxService, headerSerializer);
    }

    @Test
    void publishSuccess_savesReplyToOutbox() {
        sagaReplyPublisher.publishSuccess("saga-1", SagaStep.PROCESS_INVOICE, "corr-1");

        verify(outboxService).saveWithRouting(
            argThat(event -> event.getEventType() != null),
            eq("ProcessedInvoice"),
            eq("saga-1"),
            eq("saga.reply.invoice"),
            eq("saga-1"),
            argThat(headers -> headers.contains("SUCCESS"))
        );
    }

    @Test
    void publishSuccess_includesCorrectHeaders() {
        sagaReplyPublisher.publishSuccess("saga-abc", SagaStep.PROCESS_INVOICE, "corr-abc");

        verify(outboxService).saveWithRouting(
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            argThat(h -> h.contains("saga-abc") && h.contains("corr-abc") && h.contains("SUCCESS"))
        );
    }

    @Test
    void publishFailure_savesFailureReplyToOutbox() {
        sagaReplyPublisher.publishFailure("saga-2", SagaStep.PROCESS_INVOICE, "corr-2", "Parse error");

        verify(outboxService).saveWithRouting(
            argThat(event -> event.getEventType() != null),
            eq("ProcessedInvoice"),
            eq("saga-2"),
            eq("saga.reply.invoice"),
            eq("saga-2"),
            argThat(headers -> headers.contains("FAILURE"))
        );
    }

    @Test
    void publishFailure_includesCorrectHeaders() {
        sagaReplyPublisher.publishFailure("saga-xyz", SagaStep.PROCESS_INVOICE, "corr-xyz", "Some error");

        verify(outboxService).saveWithRouting(
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            argThat(h -> h.contains("saga-xyz") && h.contains("corr-xyz") && h.contains("FAILURE"))
        );
    }

    @Test
    void publishCompensated_savesCompensatedReplyToOutbox() {
        sagaReplyPublisher.publishCompensated("saga-3", SagaStep.PROCESS_INVOICE, "corr-3");

        verify(outboxService).saveWithRouting(
            argThat(event -> event.getEventType() != null),
            eq("ProcessedInvoice"),
            eq("saga-3"),
            eq("saga.reply.invoice"),
            eq("saga-3"),
            argThat(headers -> headers.contains("COMPENSATED"))
        );
    }

    @Test
    void publishCompensated_includesCorrectHeaders() {
        sagaReplyPublisher.publishCompensated("saga-comp", SagaStep.PROCESS_INVOICE, "corr-comp");

        verify(outboxService).saveWithRouting(
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            argThat(h -> h.contains("saga-comp") && h.contains("corr-comp") && h.contains("COMPENSATED"))
        );
    }

    @Test
    void publishSuccess_whenOutboxFails_propagatesException() {
        doThrow(new RuntimeException("Outbox unavailable"))
            .when(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> sagaReplyPublisher.publishSuccess("saga-1", SagaStep.PROCESS_INVOICE, "corr-1"));
    }
}
