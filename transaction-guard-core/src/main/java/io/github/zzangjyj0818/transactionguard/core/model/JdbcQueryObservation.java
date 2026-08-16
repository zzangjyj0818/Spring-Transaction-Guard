package io.github.zzangjyj0818.transactionguard.core.model;

/** Aggregated JDBC execution facts for one transaction. */
public record JdbcQueryObservation(long queryCount, long failedQueryCount, long totalDurationNanos) {
    public JdbcQueryObservation {
        if (queryCount < 0) throw new IllegalArgumentException("queryCount must not be negative");
        if (failedQueryCount < 0 || failedQueryCount > queryCount) {
            throw new IllegalArgumentException("failedQueryCount must be between zero and queryCount");
        }
        if (totalDurationNanos < 0) {
            throw new IllegalArgumentException("totalDurationNanos must not be negative");
        }
    }

    public static JdbcQueryObservation empty() {
        return new JdbcQueryObservation(0, 0, 0);
    }
}
