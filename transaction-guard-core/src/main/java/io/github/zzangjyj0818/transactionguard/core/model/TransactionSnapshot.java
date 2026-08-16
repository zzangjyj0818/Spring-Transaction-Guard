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
 * @param redisOperations immutable Redis operations observed inside the transaction
 * @param kafkaProducerCalls immutable Kafka producer calls observed inside the transaction
 */
public record TransactionSnapshot(
        String transactionId,
        TransactionEntryPoint entryPoint,
        Duration duration,
        TransactionOutcome outcome,
        List<ExternalCallObservation> externalCalls,
        List<RedisOperationObservation> redisOperations,
        List<KafkaProducerObservation> kafkaProducerCalls
) {

    /**
     * Creates a snapshot without Redis observations.
     *
     * <p>This constructor preserves the v0.3 source API.</p>
     */
    public TransactionSnapshot(
            String transactionId,
            TransactionEntryPoint entryPoint,
            Duration duration,
            TransactionOutcome outcome,
            List<ExternalCallObservation> externalCalls
    ) {
        this(transactionId, entryPoint, duration, outcome, externalCalls, List.of(), List.of());
    }

    /** Creates a snapshot without Kafka producer observations. */
    public TransactionSnapshot(
            String transactionId,
            TransactionEntryPoint entryPoint,
            Duration duration,
            TransactionOutcome outcome,
            List<ExternalCallObservation> externalCalls,
            List<RedisOperationObservation> redisOperations
    ) {
        this(transactionId, entryPoint, duration, outcome, externalCalls, redisOperations, List.of());
    }

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
        redisOperations = List.copyOf(Objects.requireNonNull(
                redisOperations, "redisOperations must not be null"));
        kafkaProducerCalls = List.copyOf(Objects.requireNonNull(
                kafkaProducerCalls, "kafkaProducerCalls must not be null"));
    }
}
