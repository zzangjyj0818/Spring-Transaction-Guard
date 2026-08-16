package io.github.zzangjyj0818.transactionguard.spring.transaction;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable state owned by one active imperative Spring transaction. */
public final class TransactionGuardContext {

    private final String transactionId;
    private final long startedAtNanos;
    private final TransactionEntryPoint entryPoint;
    private final String threadName;
    private final List<ExternalCallObservation> externalCalls = new ArrayList<>();

    TransactionGuardContext(
            String transactionId,
            long startedAtNanos,
            TransactionEntryPoint entryPoint,
            String threadName
    ) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        if (transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId must not be blank");
        }
        this.startedAtNanos = startedAtNanos;
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint must not be null");
        this.threadName = Objects.requireNonNull(threadName, "threadName must not be null");
    }

    /**
     * Returns this observation's unique identifier.
     *
     * @return transaction observation identifier
     */
    public String transactionId() {
        return transactionId;
    }

    /**
     * Returns the entry point captured when observation began.
     *
     * @return transaction entry point
     */
    public TransactionEntryPoint entryPoint() {
        return entryPoint;
    }

    /**
     * Returns the diagnostic name of the thread on which observation began.
     *
     * @return initial thread name
     */
    public String threadName() {
        return threadName;
    }

    /**
     * Adds an external call observation to this transaction.
     *
     * @param observation sanitized external call observation
     */
    public void addExternalCall(ExternalCallObservation observation) {
        externalCalls.add(Objects.requireNonNull(observation, "observation must not be null"));
    }

    TransactionSnapshot snapshot(long completedAtNanos, TransactionOutcome outcome) {
        long elapsedNanos = completedAtNanos >= startedAtNanos
                ? completedAtNanos - startedAtNanos
                : 0;
        return new TransactionSnapshot(
                transactionId,
                entryPoint,
                Duration.ofNanos(elapsedNanos),
                outcome,
                externalCalls
        );
    }
}
