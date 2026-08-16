package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.KafkaProducerObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Produces one TG006 violation for each Kafka producer call in a transaction. */
public final class KafkaProducerCallPolicy implements TransactionGuardPolicy {
    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        List<TransactionGuardViolation> violations = new ArrayList<>();
        for (KafkaProducerObservation call : snapshot.kafkaProducerCalls()) {
            violations.add(PolicyViolations.warn(
                    ViolationType.KAFKA_PRODUCER_CALL_IN_TRANSACTION,
                    "Kafka producer call occurred while a database transaction was active",
                    snapshot, attributes(call)));
        }
        return List.copyOf(violations);
    }

    static Map<String, Object> attributes(KafkaProducerObservation call) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("clientType", "KAFKA_TEMPLATE");
        attributes.put("operation", "SEND");
        attributes.put("durationNanos", call.durationNanos());
        attributes.put("outcome", call.outcome().name());
        if (call.exceptionType() != null) attributes.put("exceptionType", call.exceptionType());
        return Map.copyOf(attributes);
    }
}
