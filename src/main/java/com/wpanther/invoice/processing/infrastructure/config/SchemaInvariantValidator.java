package com.wpanther.invoice.processing.infrastructure.config;

import com.wpanther.invoice.processing.application.service.InvoiceProcessingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Startup validator that verifies the database index assumed by
 * {@link InvoiceProcessingService#SOURCE_INVOICE_ID_INDEX} actually exists.
 *
 * <p>If the index is missing (e.g. after a migration rename), the service
 * would silently misclassify concurrent-insert race conditions as genuine
 * data errors. Failing fast at boot surfaces the problem immediately.
 *
 * <p>The check is skipped on H2 (test profile) because H2's {@code pg_indexes}
 * catalog view is absent; a {@link BadSqlGrammarException} is caught and
 * logged as a debug-level notice rather than treated as a failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaInvariantValidator {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void validateSchemaInvariants() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?",
                Integer.class,
                InvoiceProcessingService.SOURCE_INVOICE_ID_INDEX
            );

            if (count == null || count == 0) {
                throw new IllegalStateException(
                    "Required database index '" + InvoiceProcessingService.SOURCE_INVOICE_ID_INDEX +
                    "' not found. Race-condition detection in InvoiceProcessingService will not " +
                    "function correctly. Ensure Flyway migrations have run successfully."
                );
            }

            log.debug("Schema invariant verified: index '{}' exists",
                InvoiceProcessingService.SOURCE_INVOICE_ID_INDEX);

        } catch (BadSqlGrammarException e) {
            // pg_indexes does not exist on H2 (test profile) — skip the check.
            log.debug("pg_indexes not available (likely H2 test database) — skipping schema invariant check");
        }
    }
}
