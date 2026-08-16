package io.github.zzangjyj0818.transactionguard.core.model;

/** Severity assigned to a transaction guard violation. */
public enum ViolationSeverity {
    /** Informational diagnostic. */
    INFO,
    /** Potentially unsafe usage that should be investigated. */
    WARN,
    /** Severe violation requiring immediate attention. */
    ERROR
}
