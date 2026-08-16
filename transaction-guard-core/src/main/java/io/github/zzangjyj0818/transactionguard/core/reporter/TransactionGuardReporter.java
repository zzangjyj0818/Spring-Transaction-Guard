package io.github.zzangjyj0818.transactionguard.core.reporter;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;

import java.util.List;

/** Receives policy violations and decides how they affect the calling application. */
@FunctionalInterface
public interface TransactionGuardReporter {

    /**
     * Reports all violations detected for a completed transaction.
     *
     * @param violations violations to report, possibly empty
     */
    void report(List<TransactionGuardViolation> violations);
}
