package io.github.zzangjyj0818.transactionguard.core.model;

/** Outcome observed for an external call. */
public enum ExternalCallOutcome {
    /** Call completed without an exception. */
    SUCCESS,
    /** Call ended with an exception. */
    FAILURE
}
