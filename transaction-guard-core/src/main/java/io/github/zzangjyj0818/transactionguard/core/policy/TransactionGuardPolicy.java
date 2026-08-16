package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;

import java.util.List;

/** Evaluates an immutable transaction snapshot and returns all detected violations. */
@FunctionalInterface
public interface TransactionGuardPolicy {

    /**
     * Evaluates the supplied snapshot.
     *
     * @param snapshot immutable completed transaction snapshot
     * @return immutable list of detected violations
     */
    List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot);
}
