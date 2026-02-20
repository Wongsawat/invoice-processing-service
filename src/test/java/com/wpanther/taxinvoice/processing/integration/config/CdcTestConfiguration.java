package com.wpanther.taxinvoice.processing.integration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@Profile("cdc-test")
@EnableAutoConfiguration(exclude = {
    CamelAutoConfiguration.class,
    KafkaAutoConfiguration.class
})
@ComponentScan(
    basePackages = {
        "com.wpanther.taxinvoice.processing.domain",
        "com.wpanther.taxinvoice.processing.application",
        "com.wpanther.taxinvoice.processing.infrastructure.persistence",
        "com.wpanther.taxinvoice.processing.infrastructure.config",
        "com.wpanther.taxinvoice.processing.infrastructure.messaging",
        "com.wpanther.taxinvoice.processing.infrastructure.service",
        "com.wpanther.saga.infrastructure"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*RouteConfig.*"
    )
)
@EnableJpaRepositories(basePackages = {
    "com.wpanther.taxinvoice.processing.infrastructure.persistence"
})
@EntityScan(basePackages = {
    "com.wpanther.taxinvoice.processing.infrastructure.persistence"
})
@EnableTransactionManagement
@Import(TestKafkaConsumerConfig.class)
public class CdcTestConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public JdbcTemplate testJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
