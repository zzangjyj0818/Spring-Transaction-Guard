package io.github.zzangjyj0818.transactionguard.core.model;

import java.util.Objects;

/**
 * Immutable, sanitized observation of an external call made during a transaction.
 *
 * @param clientType instrumented client type
 * @param httpMethod HTTP request method
 * @param host destination host without credentials
 * @param path destination path without query parameters
 * @param durationNanos monotonic call duration in nanoseconds
 * @param outcome call outcome
 * @param exceptionType exception class name, or {@code null} when none occurred
 */
public record ExternalCallObservation(
        ExternalClientType clientType,
        String httpMethod,
        String host,
        String path,
        long durationNanos,
        ExternalCallOutcome outcome,
        String exceptionType
) {

    /** Validates and creates a sanitized external call observation. */
    public ExternalCallObservation {
        Objects.requireNonNull(clientType, "clientType must not be null");
        httpMethod = requireText(httpMethod, "httpMethod");
        host = requireText(host, "host");
        path = requireText(path, "path");
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (exceptionType != null && exceptionType.isBlank()) {
            throw new IllegalArgumentException("exceptionType must not be blank");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
