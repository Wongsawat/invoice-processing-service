package com.invoice.processing.infrastructure.config;

import com.invoice.processing.domain.event.IntegrationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KafkaConfig
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaConfigTest {

    @Autowired
    private KafkaTemplate<String, IntegrationEvent> kafkaTemplate;

    @Autowired
    private ProducerFactory<String, IntegrationEvent> producerFactory;

    @Autowired
    private ConsumerFactory<String, IntegrationEvent> consumerFactory;

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<String, IntegrationEvent> kafkaListenerContainerFactory;

    @Test
    void testKafkaTemplateBeanCreated() {
        // Then
        assertNotNull(kafkaTemplate, "KafkaTemplate bean should be created");
        assertNotNull(kafkaTemplate.getProducerFactory(), "Producer factory should be set");
    }

    @Test
    void testProducerFactoryBeanCreated() {
        // Then
        assertNotNull(producerFactory, "ProducerFactory bean should be created");
        assertNotNull(producerFactory.getConfigurationProperties(), "Producer config should be set");
    }

    @Test
    void testConsumerFactoryBeanCreated() {
        // Then
        assertNotNull(consumerFactory, "ConsumerFactory bean should be created");
        assertNotNull(consumerFactory.getConfigurationProperties(), "Consumer config should be set");
    }

    @Test
    void testKafkaListenerContainerFactoryBeanCreated() {
        // Then
        assertNotNull(kafkaListenerContainerFactory, "KafkaListenerContainerFactory bean should be created");
        assertNotNull(kafkaListenerContainerFactory.getConsumerFactory(), "Consumer factory should be set");
    }

    @Test
    void testProducerFactoryConfiguration() {
        // When
        var config = producerFactory.getConfigurationProperties();

        // Then
        assertEquals("localhost:9092", config.get("bootstrap.servers"), "Bootstrap servers should be configured");
        assertEquals("all", config.get("acks"), "Acks should be 'all' for reliability");
        assertEquals(3, config.get("retries"), "Retries should be configured");
        assertEquals(true, config.get("enable.idempotence"), "Idempotence should be enabled");
    }

    @Test
    void testConsumerFactoryConfiguration() {
        // When
        var config = consumerFactory.getConfigurationProperties();

        // Then
        assertEquals("localhost:9092", config.get("bootstrap.servers"), "Bootstrap servers should be configured");
        assertEquals("test-group", config.get("group.id"), "Group ID should be configured");
        assertEquals("earliest", config.get("auto.offset.reset"), "Auto offset reset should be 'earliest'");
        assertEquals(false, config.get("enable.auto.commit"), "Auto commit should be disabled");
    }

    @Test
    void testKafkaListenerContainerFactoryConfiguration() {
        // Then
        assertEquals(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL,
            kafkaListenerContainerFactory.getContainerProperties().getAckMode(),
            "Ack mode should be MANUAL"
        );
        assertNotNull(kafkaListenerContainerFactory.getConsumerFactory(),
            "Consumer factory should be set");
    }
}
