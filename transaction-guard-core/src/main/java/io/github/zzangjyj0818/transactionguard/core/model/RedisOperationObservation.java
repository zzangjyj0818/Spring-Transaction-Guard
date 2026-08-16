package io.github.zzangjyj0818.transactionguard.core.model;

import java.util.Objects;

/**
 * Immutable, privacy-safe observation of an imperative Redis operation.
 *
 * <p>The model intentionally cannot contain a Redis key, value, command arguments,
 * connection URI, credentials, or an exception message.</p>
 *
 * @param clientType instrumented Redis client type
 * @param commandCategory coarse command category
 * @param durationNanos monotonic operation duration in nanoseconds
 * @param outcome operation outcome
 * @param exceptionType exception class name, or {@code null} when none occurred
 */
public record RedisOperationObservation(
        RedisClientType clientType,
        RedisCommandCategory commandCategory,
        long durationNanos,
        ExternalCallOutcome outcome,
        String exceptionType
) {

    /** Validates and creates a privacy-safe Redis observation. */
    public RedisOperationObservation {
        Objects.requireNonNull(clientType, "clientType must not be null");
        Objects.requireNonNull(commandCategory, "commandCategory must not be null");
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (exceptionType != null && exceptionType.isBlank()) {
            throw new IllegalArgumentException("exceptionType must not be blank");
        }
    }
}
