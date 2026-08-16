package io.github.zzangjyj0818.transactionguard.core.model;

/** Final outcome of a database transaction. */
public enum TransactionOutcome {
    /** Transaction committed successfully. */
    COMMITTED,
    /** Transaction rolled back. */
    ROLLED_BACK,
    /** Completion status could not be determined. */
    UNKNOWN
}
