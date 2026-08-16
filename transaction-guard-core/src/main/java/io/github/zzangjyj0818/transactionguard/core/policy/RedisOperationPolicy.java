package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.RedisOperationObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Produces one TG004 violation for every Redis operation in a transaction. */
public final class RedisOperationPolicy implements TransactionGuardPolicy {

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        List<TransactionGuardViolation> violations = new ArrayList<>();
        for (RedisOperationObservation operation : snapshot.redisOperations()) {
            violations.add(PolicyViolations.warn(
                    ViolationType.REDIS_OPERATION_IN_TRANSACTION,
                    "Redis operation occurred while a database transaction was active",
                    snapshot,
                    attributes(operation)));
        }
        return List.copyOf(violations);
    }

    static Map<String, Object> attributes(RedisOperationObservation operation) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("clientType", operation.clientType().name());
        attributes.put("commandCategory", operation.commandCategory().name());
        attributes.put("durationNanos", operation.durationNanos());
        attributes.put("outcome", operation.outcome().name());
        if (operation.exceptionType() != null) {
            attributes.put("exceptionType", operation.exceptionType());
        }
        return Map.copyOf(attributes);
    }
}
