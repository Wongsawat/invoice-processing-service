package com.wpanther.invoice.processing.integration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.invoice.processing.infrastructure.config.KafkaTopicsProperties;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(KafkaTopicsProperties.class)
@EnableAutoConfiguration(exclude = {
    CamelAutoConfiguration.class,
    KafkaAutoConfiguration.class
})
@ComponentScan(
    basePackages = {
        "com.wpanther.invoice.processing.domain",
        "com.wpanther.invoice.processing.application",
        "com.wpanther.invoice.processing.infrastructure.adapter.out.persistence",
        "com.wpanther.invoice.processing.infrastructure.adapter.out.messaging",
        "com.wpanther.invoice.processing.infrastructure.adapter.in.messaging",
        "com.wpanther.invoice.processing.infrastructure.adapter.out.parsing",
        "com.wpanther.invoice.processing.infrastructure.config",
        "com.wpanther.saga.infrastructure"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*RouteConfig.*"
    )
)
@EnableJpaRepositories(basePackages = {
    "com.wpanther.invoice.processing.infrastructure.adapter.out.persistence"
})
@EntityScan(basePackages = {
    "com.wpanther.invoice.processing.infrastructure.adapter.out.persistence"
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
