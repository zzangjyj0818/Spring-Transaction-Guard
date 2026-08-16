package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Produces TG003 for each external HTTP call exceeding the configured threshold. */
public final class SlowExternalHttpCallPolicy implements TransactionGuardPolicy {

    private final Duration threshold;
    private final Predicate<ExternalCallObservation> violationCandidate;

    /**
     * Creates a slow external HTTP call policy.
     *
     * @param threshold non-negative maximum external call duration
     */
    public SlowExternalHttpCallPolicy(Duration threshold) {
        this(threshold, call -> true);
    }

    /** Creates a policy that evaluates only calls accepted by the predicate. */
    public SlowExternalHttpCallPolicy(
            Duration threshold,
            Predicate<ExternalCallObservation> violationCandidate
    ) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        if (threshold.isNegative()) {
            throw new IllegalArgumentException("threshold must not be negative");
        }
        this.threshold = threshold;
        this.violationCandidate = Objects.requireNonNull(
                violationCandidate, "violationCandidate must not be null");
    }

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        List<TransactionGuardViolation> violations = new ArrayList<>();
        long thresholdNanos = threshold.toNanos();
        for (ExternalCallObservation call : snapshot.externalCalls()) {
            if (!violationCandidate.test(call) || call.durationNanos() <= thresholdNanos) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>(ExternalHttpCallPolicy.attributes(call));
            attributes.put("thresholdNanos", thresholdNanos);
            violations.add(PolicyViolations.warn(
                    ViolationType.SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION,
                    "External HTTP call duration exceeded the configured threshold",
                    snapshot,
                    attributes
            ));
        }
        return List.copyOf(violations);
    }
}
