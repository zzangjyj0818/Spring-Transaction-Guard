package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Produces TG001 when transaction duration is greater than the configured threshold. */
public final class LongTransactionPolicy implements TransactionGuardPolicy {

    private final Duration threshold;

    /**
     * Creates a long transaction policy.
     *
     * @param threshold non-negative maximum transaction duration
     */
    public LongTransactionPolicy(Duration threshold) {
        this.threshold = requireThreshold(threshold);
    }

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (snapshot.duration().compareTo(threshold) <= 0) {
            return List.of();
        }
        return List.of(PolicyViolations.warn(
                ViolationType.LONG_TRANSACTION,
                "Transaction duration exceeded the configured threshold",
                snapshot,
                Map.of("durationNanos", snapshot.duration().toNanos(), "thresholdNanos", threshold.toNanos())
        ));
    }

    private static Duration requireThreshold(Duration threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        if (threshold.isNegative()) {
            throw new IllegalArgumentException("threshold must not be negative");
        }
        return threshold;
    }
}
