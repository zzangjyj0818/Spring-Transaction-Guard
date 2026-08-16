package io.github.zzangjyj0818.transactionguard.core.model;

import java.util.Objects;

/**
 * Business method associated with a transaction observation.
 *
 * @param className fully qualified declaring class name
 * @param methodName invoked method name
 * @param signature developer-friendly method signature
 */
public record TransactionEntryPoint(String className, String methodName, String signature) {

    /** Validates and creates a transaction entry point. */
    public TransactionEntryPoint {
        className = requireText(className, "className");
        methodName = requireText(methodName, "methodName");
        signature = requireText(signature, "signature");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
