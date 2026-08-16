package io.github.zzangjyj0818.transactionguard.core.model;

import java.util.Objects;

/** Privacy-safe observation of a Kafka producer send invocation. */
public record KafkaProducerObservation(
        long durationNanos,
        ExternalCallOutcome outcome,
        String exceptionType
) {
    public KafkaProducerObservation {
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (exceptionType != null && exceptionType.isBlank()) {
            throw new IllegalArgumentException("exceptionType must not be blank");
        }
    }
}
