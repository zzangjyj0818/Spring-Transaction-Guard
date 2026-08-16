package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.KafkaProducerObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Produces TG007 for slow Kafka producer send invocations. */
public final class SlowKafkaProducerCallPolicy implements TransactionGuardPolicy {
    private final long thresholdNanos;

    public SlowKafkaProducerCallPolicy(Duration threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        if (threshold.isNegative()) throw new IllegalArgumentException("threshold must not be negative");
        thresholdNanos = threshold.toNanos();
    }

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        List<TransactionGuardViolation> violations = new ArrayList<>();
        for (KafkaProducerObservation call : snapshot.kafkaProducerCalls()) {
            if (call.durationNanos() <= thresholdNanos) continue;
            Map<String, Object> attributes = new LinkedHashMap<>(KafkaProducerCallPolicy.attributes(call));
            attributes.put("thresholdNanos", thresholdNanos);
            violations.add(PolicyViolations.warn(
                    ViolationType.SLOW_KAFKA_PRODUCER_CALL_IN_TRANSACTION,
                    "Kafka producer call duration exceeded the configured threshold",
                    snapshot, attributes));
        }
        return List.copyOf(violations);
    }
}
