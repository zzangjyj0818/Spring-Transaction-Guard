package io.github.zzangjyj0818.transactionguard.spring.jdbc;

import io.github.zzangjyj0818.transactionguard.spring.transaction.MonotonicClock;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;

import java.util.Objects;

/** Records aggregate JDBC query facts without SQL text or connection metadata. */
public final class TransactionGuardJdbcRecorder {
    private final TransactionGuardContextRegistry registry;
    private final MonotonicClock clock;

    public TransactionGuardJdbcRecorder(TransactionGuardContextRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = MonotonicClock.system();
    }

    public boolean isTransactionObserved() { return registry.currentContext().isPresent(); }
    public long nanoTime() { return clock.nanoTime(); }

    public void record(long durationNanos, boolean failed) {
        registry.currentContext().ifPresent(context -> context.recordJdbcQuery(durationNanos, failed));
    }
}
