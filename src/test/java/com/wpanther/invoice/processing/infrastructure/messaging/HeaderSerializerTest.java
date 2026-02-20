package com.wpanther.invoice.processing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeaderSerializerTest {

    @Mock
    private ObjectMapper mockObjectMapper;

    private HeaderSerializer headerSerializer;

    @BeforeEach
    void setUp() {
        headerSerializer = new HeaderSerializer(new ObjectMapper());
    }

    @Test
    void toJson_withValidMap_returnsJsonString() {
        Map<String, String> headers = Map.of(
            "sagaId", "saga-123",
            "correlationId", "corr-456",
            "status", "SUCCESS"
        );

        String result = headerSerializer.toJson(headers);

        assertNotNull(result);
        assertTrue(result.contains("saga-123"));
        assertTrue(result.contains("corr-456"));
        assertTrue(result.contains("SUCCESS"));
    }

    @Test
    void toJson_withEmptyMap_returnsEmptyJsonObject() {
        String result = headerSerializer.toJson(Map.of());

        assertNotNull(result);
        assertEquals("{}", result);
    }

    @Test
    void toJson_withSingleEntry_returnsJsonString() {
        Map<String, String> headers = Map.of("key", "value");

        String result = headerSerializer.toJson(headers);

        assertNotNull(result);
        assertTrue(result.contains("\"key\""));
        assertTrue(result.contains("\"value\""));
    }

    @Test
    void toJson_whenJsonProcessingFails_returnsFallbackEmptyJson() throws JsonProcessingException {
        when(mockObjectMapper.writeValueAsString(any())).thenThrow(JsonProcessingException.class);
        HeaderSerializer serializerWithMock = new HeaderSerializer(mockObjectMapper);

        String result = serializerWithMock.toJson(Map.of("k", "v"));

        assertEquals("{}", result);
    }
}
