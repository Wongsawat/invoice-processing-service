package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.CompensationLogEntry;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompensationLogRepositoryImplTest {

    @Mock
    private JpaCompensationLogRepository jpaRepository;

    @InjectMocks
    private CompensationLogRepositoryImpl repository;

    @Test
    void save_delegatesToJpa_withCorrectEntityFields() {
        InvoiceId invoiceId = InvoiceId.generate();
        CompensationLogEntry entry = CompensationLogEntry.compensated(
            "doc-1", invoiceId, "INV-001", "saga-1", "corr-1");

        repository.save(entry);

        ArgumentCaptor<CompensationLogEntity> captor =
            ArgumentCaptor.forClass(CompensationLogEntity.class);
        verify(jpaRepository).save(captor.capture());

        CompensationLogEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(entry.id());
        assertThat(entity.getSourceInvoiceId()).isEqualTo("doc-1");
        assertThat(entity.getInvoiceId()).isEqualTo(invoiceId.value());
        assertThat(entity.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(entity.getSagaId()).isEqualTo("saga-1");
        assertThat(entity.getReason()).isEqualTo("COMPENSATED");
    }

    @Test
    void save_alreadyAbsent_setsNullInvoiceFields() {
        CompensationLogEntry entry = CompensationLogEntry.alreadyAbsent(
            "doc-2", "saga-2", "corr-2");

        repository.save(entry);

        ArgumentCaptor<CompensationLogEntity> captor =
            ArgumentCaptor.forClass(CompensationLogEntity.class);
        verify(jpaRepository).save(captor.capture());

        CompensationLogEntity entity = captor.getValue();
        assertThat(entity.getInvoiceId()).isNull();
        assertThat(entity.getInvoiceNumber()).isNull();
        assertThat(entity.getReason()).isEqualTo("ALREADY_ABSENT");
    }
}
