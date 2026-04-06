package com.wpanther.invoice.processing.integration.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Configuration
@Profile({"cdc-test", "consumer-test"})
public class TestKafkaConsumerConfig {

    private static final String BOOTSTRAP_SERVERS = "localhost:9093";

    @Bean
    public Properties kafkaAdminProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);
        return props;
    }

    @Bean
    public KafkaConsumer<String, String> testKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "invoice-cdc-test-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }

    public void createTopics() {
        try (AdminClient adminClient = AdminClient.create(
                Collections.singletonMap("bootstrap.servers", BOOTSTRAP_SERVERS))) {
            adminClient.createTopics(Arrays.asList(
                new NewTopic("invoice.processed", 1, (short) 1),
                new NewTopic("saga.reply.invoice", 1, (short) 1),
                new NewTopic("saga.command.invoice", 1, (short) 1),
                new NewTopic("saga.compensation.invoice", 1, (short) 1),
                new NewTopic("invoice.processing.dlq", 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Topics may already exist
        }
    }
}
