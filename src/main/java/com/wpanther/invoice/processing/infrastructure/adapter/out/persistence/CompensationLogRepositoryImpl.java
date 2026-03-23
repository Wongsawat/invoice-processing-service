package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.CompensationLogEntry;
import com.wpanther.invoice.processing.domain.port.out.CompensationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CompensationLogRepositoryImpl implements CompensationLogRepository {

    private final JpaCompensationLogRepository jpaRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(CompensationLogEntry entry) {
        jpaRepository.save(CompensationLogEntity.fromDomain(entry));
        log.debug("Saved compensation log entry id={} for document={} reason={}",
            entry.id(), entry.sourceInvoiceId(), entry.reason());
    }
}
