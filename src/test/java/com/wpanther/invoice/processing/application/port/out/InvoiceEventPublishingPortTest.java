package com.wpanther.invoice.processing.application.port.out;

import com.wpanther.invoice.processing.domain.event.InvoiceProcessedDomainEvent;
import com.wpanther.invoice.processing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;

class InvoiceEventPublishingPortTest {

    @Test
    void shouldAcceptDomainEventPublishCall() {
        InvoiceEventPublishingPort port = mock(InvoiceEventPublishingPort.class);
        InvoiceProcessedDomainEvent event = new InvoiceProcessedDomainEvent(
            "DOC-550e8400-e29b-41d4-a716-446655440000", "INV-001",
            new Money(new BigDecimal("100.00"), "THB"),
            "saga-1", "corr-1", Instant.now()
        );

        port.publish(event);

        verify(port).publish(event);
    }
}
