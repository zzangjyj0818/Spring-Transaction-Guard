package io.github.zzangjyj0818.transactionguard.spring.redis;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.RedisClientType;
import io.github.zzangjyj0818.transactionguard.core.model.RedisCommandCategory;
import io.github.zzangjyj0818.transactionguard.core.model.RedisOperationObservation;
import io.github.zzangjyj0818.transactionguard.spring.transaction.MonotonicClock;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;

import java.util.Objects;

/** Records privacy-safe Redis operations into the current transaction context. */
public final class TransactionGuardRedisRecorder {

    private final TransactionGuardContextRegistry registry;
    private final MonotonicClock clock;

    public TransactionGuardRedisRecorder(TransactionGuardContextRegistry registry) {
        this(registry, MonotonicClock.system());
    }

    TransactionGuardRedisRecorder(TransactionGuardContextRegistry registry, MonotonicClock clock) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public boolean isTransactionObserved() {
        return registry.currentContext().isPresent();
    }

    public long nanoTime() {
        return clock.nanoTime();
    }

    public void recordSuccess(RedisCommandCategory category, long durationNanos) {
        record(category, durationNanos, ExternalCallOutcome.SUCCESS, null);
    }

    public void recordFailure(RedisCommandCategory category, long durationNanos, Throwable failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        record(category, durationNanos, ExternalCallOutcome.FAILURE, failure.getClass().getName());
    }

    private void record(RedisCommandCategory category, long durationNanos,
                        ExternalCallOutcome outcome, String exceptionType) {
        registry.currentContext().ifPresent(context -> context.addRedisOperation(new RedisOperationObservation(
                RedisClientType.REDIS_TEMPLATE,
                Objects.requireNonNull(category, "category must not be null"),
                Math.max(0, durationNanos), outcome, exceptionType)));
    }
}
