package com.wpanther.invoice.processing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HeaderSerializer {

    private static final Logger log = LoggerFactory.getLogger(HeaderSerializer.class);

    private final ObjectMapper objectMapper;

    public HeaderSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Map<String, String> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox headers to JSON", e);
            return null;
        }
    }
}
