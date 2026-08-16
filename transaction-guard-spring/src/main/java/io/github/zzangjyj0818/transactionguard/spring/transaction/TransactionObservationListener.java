package io.github.zzangjyj0818.transactionguard.spring.transaction;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;

import java.util.List;

/** Receives an immutable transaction result after all Guard policies have been evaluated. */
@FunctionalInterface
public interface TransactionObservationListener {

    /**
     * Observes one completed transaction and its evaluated violations.
     *
     * @param snapshot immutable transaction snapshot
     * @param violations immutable evaluated violations
     */
    void onCompleted(TransactionSnapshot snapshot, List<TransactionGuardViolation> violations);
}
