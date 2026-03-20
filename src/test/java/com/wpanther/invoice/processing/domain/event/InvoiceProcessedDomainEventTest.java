package com.wpanther.invoice.processing.domain.event;

import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceProcessedDomainEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        InvoiceId id = InvoiceId.generate();
        Money total = new Money(new BigDecimal("1000.00"), "THB");
        Instant now = Instant.now();

        InvoiceProcessedDomainEvent event = new InvoiceProcessedDomainEvent(
            id, "INV-001", total, "saga-123", "corr-123", now
        );

        assertThat(event.invoiceId()).isEqualTo(id);
        assertThat(event.invoiceNumber()).isEqualTo("INV-001");
        assertThat(event.total()).isEqualTo(total);
        assertThat(event.sagaId()).isEqualTo("saga-123");
        assertThat(event.correlationId()).isEqualTo("corr-123");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void shouldBeEqualWhenAllFieldsMatch() {
        InvoiceId id = InvoiceId.generate();
        Money total = new Money(new BigDecimal("500.00"), "THB");
        Instant now = Instant.now();

        InvoiceProcessedDomainEvent e1 = new InvoiceProcessedDomainEvent(id, "INV-002", total, "saga-1", "c-1", now);
        InvoiceProcessedDomainEvent e2 = new InvoiceProcessedDomainEvent(id, "INV-002", total, "saga-1", "c-1", now);

        assertThat(e1).isEqualTo(e2);
    }
}
