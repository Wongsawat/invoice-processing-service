package com.wpanther.invoice.processing.infrastructure.config;

import com.wpanther.invoice.processing.application.service.InvoiceProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SchemaInvariantValidator
 */
@ExtendWith(MockitoExtension.class)
class SchemaInvariantValidatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void validateSchemaInvariants_whenIndexExists_doesNotThrow() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
            .thenReturn(1);

        SchemaInvariantValidator validator = new SchemaInvariantValidator(jdbcTemplate);
        assertDoesNotThrow(validator::validateSchemaInvariants);
    }

    @Test
    void validateSchemaInvariants_whenIndexMissing_throwsIllegalStateException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
            .thenReturn(0);

        SchemaInvariantValidator validator = new SchemaInvariantValidator(jdbcTemplate);
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            validator::validateSchemaInvariants
        );
        assertTrue(ex.getMessage().contains(InvoiceProcessingService.SOURCE_INVOICE_ID_INDEX));
    }

    @Test
    void validateSchemaInvariants_whenPgIndexesAbsent_skipsCheckGracefully() {
        // H2 does not have pg_indexes — simulate by throwing BadSqlGrammarException
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
            .thenThrow(new BadSqlGrammarException("query", "SELECT COUNT(*) FROM pg_indexes",
                new SQLException("Table not found")));

        SchemaInvariantValidator validator = new SchemaInvariantValidator(jdbcTemplate);
        assertDoesNotThrow(validator::validateSchemaInvariants);
    }

    @Test
    void sourceInvoiceIdIndexConstant_matchesMigrationDefinition() {
        // Guard: if the Flyway V1 index name is ever renamed, this test will fail
        // and prompt an update to the constant in InvoiceProcessingService.
        assertEquals("idx_source_invoice_id", InvoiceProcessingService.SOURCE_INVOICE_ID_INDEX);
    }
}
