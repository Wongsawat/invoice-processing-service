package com.wpanther.invoice.processing.infrastructure.config;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Apache Camel producer template.
 */
@Configuration
public class CamelProducerConfig {

    /**
     * Create a ProducerTemplate bean for sending messages to Camel routes.
     *
     * @param camelContext the Camel context
     * @return a ProducerTemplate instance
     */
    @Bean
    public ProducerTemplate producerTemplate(CamelContext camelContext) {
        return camelContext.createProducerTemplate();
    }
}
