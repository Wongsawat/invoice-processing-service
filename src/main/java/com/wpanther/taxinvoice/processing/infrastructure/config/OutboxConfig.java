package com.wpanther.taxinvoice.processing.infrastructure.config;

import com.wpanther.taxinvoice.processing.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.taxinvoice.processing.infrastructure.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

    @Bean
    public OutboxEventRepository outboxEventRepository(SpringDataOutboxRepository springRepository) {
        return new JpaOutboxEventRepository(springRepository);
    }
}
