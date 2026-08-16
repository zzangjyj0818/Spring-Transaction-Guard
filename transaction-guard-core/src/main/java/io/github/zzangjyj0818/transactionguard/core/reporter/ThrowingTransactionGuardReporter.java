package io.github.zzangjyj0818.transactionguard.core.reporter;

import io.github.zzangjyj0818.transactionguard.core.exception.TransactionGuardViolationException;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;

import java.util.List;
import java.util.Objects;

/** Test-oriented reporter that throws when one or more violations are present. */
public final class ThrowingTransactionGuardReporter implements TransactionGuardReporter {

    /** Creates a throwing reporter. */
    public ThrowingTransactionGuardReporter() {
    }

    @Override
    public void report(List<TransactionGuardViolation> violations) {
        Objects.requireNonNull(violations, "violations must not be null");
        if (!violations.isEmpty()) {
            throw new TransactionGuardViolationException(violations);
        }
    }
}
