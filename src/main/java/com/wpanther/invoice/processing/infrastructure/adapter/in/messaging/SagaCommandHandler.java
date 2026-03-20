package com.wpanther.invoice.processing.infrastructure.adapter.in.messaging;

import com.wpanther.invoice.processing.application.port.in.CompensateInvoiceUseCase;
import com.wpanther.invoice.processing.application.port.in.ProcessInvoiceUseCase;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.CompensateInvoiceCommand;
import com.wpanther.invoice.processing.infrastructure.adapter.in.messaging.dto.ProcessInvoiceCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saga command handler - driving adapter that receives Kafka messages and calls use cases.
 * Routes ProcessInvoiceCommand → ProcessInvoiceUseCase.process()
 * Routes CompensateInvoiceCommand → CompensateInvoiceUseCase.compensate()
 * The use case handles reply publishing internally.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ProcessInvoiceUseCase processInvoiceUseCase;
    private final CompensateInvoiceUseCase compensateInvoiceUseCase;

    /**
     * Handle a ProcessInvoiceCommand from saga orchestrator.
     * Delegates to use case which handles business logic and reply publishing.
     *
     * <h3>Exception contract</h3>
     * <p>{@link ProcessInvoiceUseCase.InvoiceProcessingException} is thrown by
     * {@code process()} <em>only after</em> the FAILURE saga reply has been successfully
     * committed to the outbox table. Catching it here and returning normally tells Camel
     * "this message is done" — the Kafka offset is committed and the orchestrator already
     * has the reply.
     *
     * <p>Any <em>other</em> exception (e.g., a transient DB outage that prevented the
     * outbox write inside {@code publishFailure()}) is intentionally <em>not</em> caught.
     * It propagates to the Camel dead-letter channel, which retries the message. On retry,
     * the idempotency check finds the already-persisted invoice and publishes SUCCESS,
     * or reruns the processing. This ensures the orchestrator always receives a reply.
     */
    public void handleProcessCommand(ProcessInvoiceCommand command) {
        log.info("Handling ProcessInvoiceCommand for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        try {
            processInvoiceUseCase.process(
                command.getDocumentId(),
                command.getXmlContent(),
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );
        } catch (ProcessInvoiceUseCase.InvoiceProcessingException e) {
            // FAILURE reply was already committed to the outbox by the use case.
            // Return normally so Camel commits the Kafka offset.
            log.error("Failed to process invoice for saga {}: {}",
                command.getSagaId(), e.toString(), e);
        }
    }

    /**
     * Handle a CompensateInvoiceCommand from saga orchestrator.
     * Delegates to use case which handles business logic and reply publishing.
     *
     * <h3>Exception contract</h3>
     * <p>On success, {@code compensate()} commits a COMPENSATED reply and returns normally.
     *
     * <p>On failure, {@code compensate()} commits a FAILURE reply (via its own
     * {@code REQUIRES_NEW} transaction) and throws
     * {@link CompensateInvoiceUseCase.InvoiceCompensationException}.
     * That exception propagates to Camel's Dead Letter Channel, triggering a retry.
     * Retries are safe because {@code deleteById} is a no-op when the entity is absent.
     */
    public void handleCompensation(CompensateInvoiceCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        compensateInvoiceUseCase.compensate(
            command.getDocumentId(),
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId()
        );
    }
}
