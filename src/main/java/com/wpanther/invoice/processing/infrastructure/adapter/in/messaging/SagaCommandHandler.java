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
     * <p>{@link CompensateInvoiceUseCase.InvoiceCompensationException} is thrown by
     * {@code compensate()} <em>only after</em> the FAILURE saga reply has been successfully
     * committed to the outbox table (via a {@code REQUIRES_NEW} inner transaction).
     * Catching it here and returning normally tells Camel "this message is done" — the
     * Kafka offset is committed and the orchestrator already has the FAILURE reply.
     *
     * <p>Propagating would trigger a Camel retry. On retry, the invoice is absent, so
     * a COMPENSATED reply is committed. The orchestrator then receives FAILURE followed by
     * COMPENSATED for the same step. Because the orchestrator's retry counter is already
     * exhausted (it was exhausted during the forward phase that triggered compensation),
     * it cannot find a matching SENT command record for the COMPENSATED reply. It then
     * falls through to {@code advanceTo(nextStep)} on a COMPENSATING-state saga, which
     * throws {@code IllegalStateException("Can only advance when saga is IN_PROGRESS")}.
     *
     * <p>Any <em>other</em> exception (e.g., a transient DB outage that prevented
     * {@code publishFailure()} from committing) is intentionally <em>not</em> caught.
     * It propagates to the Camel dead-letter channel, which retries the compensation
     * message. On retry, the idempotent {@code alreadyAbsent} path handles the deleted
     * invoice correctly without sending a conflicting reply.
     */
    public void handleCompensation(CompensateInvoiceCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.getSagaId(), command.getDocumentId());

        try {
            compensateInvoiceUseCase.compensate(
                command.getDocumentId(),
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );
        } catch (CompensateInvoiceUseCase.InvoiceCompensationException e) {
            // FAILURE reply was already committed to the outbox by the use case.
            // Return normally so Camel commits the Kafka offset.
            log.error("Failed to compensate invoice for saga {}: {}",
                command.getSagaId(), e.toString(), e);
        }
    }
}
