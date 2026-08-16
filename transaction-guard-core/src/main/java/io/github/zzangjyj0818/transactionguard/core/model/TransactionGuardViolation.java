package io.github.zzangjyj0818.transactionguard.core.model;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable policy violation reported by Transaction Guard.
 *
 * @param code stable violation code
 * @param type violation type corresponding to {@code code}
 * @param severity violation severity
 * @param message human-readable explanation without sensitive data
 * @param transaction immutable transaction snapshot
 * @param attributes immutable structured diagnostic attributes
 */
public record TransactionGuardViolation(
        String code,
        ViolationType type,
        ViolationSeverity severity,
        String message,
        TransactionSnapshot transaction,
        Map<String, Object> attributes
) {

    /** Validates and creates an immutable transaction guard violation. */
    public TransactionGuardViolation {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(code, "code must not be null");
        if (!type.code().equals(code)) {
            throw new IllegalArgumentException("code must match violation type code " + type.code());
        }
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        Objects.requireNonNull(transaction, "transaction must not be null");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
    }
}
