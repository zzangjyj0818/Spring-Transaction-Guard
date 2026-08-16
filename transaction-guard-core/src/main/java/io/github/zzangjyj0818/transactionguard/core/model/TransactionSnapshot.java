package io.github.zzangjyj0818.transactionguard.core.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Immutable transaction state supplied to policies after transaction completion.
 *
 * @param transactionId unique transaction observation identifier
 * @param entryPoint business entry point associated with the transaction
 * @param duration transaction duration measured with a monotonic clock
 * @param outcome final transaction outcome
 * @param externalCalls immutable external calls observed inside the transaction
 */
public record TransactionSnapshot(
        String transactionId,
        TransactionEntryPoint entryPoint,
        Duration duration,
        TransactionOutcome outcome,
        List<ExternalCallObservation> externalCalls
) {

    /** Validates and creates an immutable transaction snapshot. */
    public TransactionSnapshot {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        if (transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId must not be blank");
        }
        Objects.requireNonNull(entryPoint, "entryPoint must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        Objects.requireNonNull(outcome, "outcome must not be null");
        externalCalls = List.copyOf(Objects.requireNonNull(externalCalls, "externalCalls must not be null"));
    }
}
