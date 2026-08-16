package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Experimental global transaction-level JDBC query budget (TG008). */
public final class QueryBudgetPolicy implements TransactionGuardPolicy {
    private final long maxQueries;

    public QueryBudgetPolicy(long maxQueries) {
        if (maxQueries < 0) throw new IllegalArgumentException("maxQueries must not be negative");
        this.maxQueries = maxQueries;
    }

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        long actual = snapshot.jdbcQueries().queryCount();
        if (actual <= maxQueries) return List.of();
        return List.of(PolicyViolations.warn(
                ViolationType.QUERY_BUDGET_EXCEEDED,
                "JDBC query count exceeded the configured transaction budget",
                snapshot,
                Map.of("queryCount", actual, "maxQueries", maxQueries,
                        "failedQueryCount", snapshot.jdbcQueries().failedQueryCount())));
    }
}
