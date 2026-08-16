package io.github.zzangjyj0818.transactionguard.spring.transaction;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Detects whether the current thread is associated with an actual Spring transaction. */
public final class ActualTransactionDetector {

    /** Creates an actual transaction detector. */
    public ActualTransactionDetector() {
    }

    /**
     * Returns whether an actual transaction and synchronization are active.
     *
     * @return {@code true} when observation can register for transaction completion
     */
    public boolean isObservationAvailable() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }
}
