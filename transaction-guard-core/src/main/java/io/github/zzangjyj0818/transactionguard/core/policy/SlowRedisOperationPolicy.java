package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.RedisOperationObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Produces TG005 for Redis operations exceeding the configured threshold. */
public final class SlowRedisOperationPolicy implements TransactionGuardPolicy {

    private final long thresholdNanos;

    public SlowRedisOperationPolicy(Duration threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        if (threshold.isNegative()) {
            throw new IllegalArgumentException("threshold must not be negative");
        }
        this.thresholdNanos = threshold.toNanos();
    }

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        List<TransactionGuardViolation> violations = new ArrayList<>();
        for (RedisOperationObservation operation : snapshot.redisOperations()) {
            if (operation.durationNanos() <= thresholdNanos) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>(RedisOperationPolicy.attributes(operation));
            attributes.put("thresholdNanos", thresholdNanos);
            violations.add(PolicyViolations.warn(
                    ViolationType.SLOW_REDIS_OPERATION_IN_TRANSACTION,
                    "Redis operation duration exceeded the configured threshold",
                    snapshot,
                    attributes));
        }
        return List.copyOf(violations);
    }
}
