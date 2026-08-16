package io.github.zzangjyj0818.transactionguard.core.exception;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Exception raised by the throwing reporter with every detected violation. */
public final class TransactionGuardViolationException extends RuntimeException {

    /** Immutable violations that caused this exception. */
    private final List<TransactionGuardViolation> violations;

    /**
     * Creates an exception containing all violations.
     *
     * @param violations non-empty violation list
     */
    public TransactionGuardViolationException(List<TransactionGuardViolation> violations) {
        super(message(violations));
        this.violations = List.copyOf(violations);
    }

    /**
     * Returns an immutable list containing every violation that caused this exception.
     *
     * @return all violations
     */
    public List<TransactionGuardViolation> violations() {
        return violations;
    }

    private static String message(List<TransactionGuardViolation> violations) {
        Objects.requireNonNull(violations, "violations must not be null");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("violations must not be empty");
        }
        if (violations.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("violations must not contain null");
        }
        String codes = violations.stream()
                .map(TransactionGuardViolation::code)
                .collect(Collectors.joining(","));
        return "Transaction Guard detected " + violations.size() + " violation(s): " + codes;
    }
}
