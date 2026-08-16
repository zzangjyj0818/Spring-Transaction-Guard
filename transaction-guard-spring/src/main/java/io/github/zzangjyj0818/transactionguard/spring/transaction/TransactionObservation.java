package io.github.zzangjyj0818.transactionguard.spring.transaction;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Registers exactly one guard observation for the current Spring transaction. */
public final class TransactionObservation {

    private final ActualTransactionDetector transactionDetector;
    private final TransactionGuardContextRegistry contextRegistry;
    private final MonotonicClock clock;
    private final Supplier<String> transactionIdGenerator;
    private final List<TransactionGuardPolicy> policies;
    private final TransactionGuardReporter reporter;

    /**
     * Creates a transaction observation using UUID identifiers and the system monotonic clock.
     *
     * @param transactionDetector actual transaction detector
     * @param contextRegistry transaction resource registry
     * @param policies policies evaluated after completion
     * @param reporter violation reporter
     */
    public TransactionObservation(
            ActualTransactionDetector transactionDetector,
            TransactionGuardContextRegistry contextRegistry,
            List<TransactionGuardPolicy> policies,
            TransactionGuardReporter reporter
    ) {
        this(transactionDetector, contextRegistry, MonotonicClock.system(),
                () -> UUID.randomUUID().toString(), policies, reporter);
    }

    TransactionObservation(
            ActualTransactionDetector transactionDetector,
            TransactionGuardContextRegistry contextRegistry,
            MonotonicClock clock,
            Supplier<String> transactionIdGenerator,
            List<TransactionGuardPolicy> policies,
            TransactionGuardReporter reporter
    ) {
        this.transactionDetector = Objects.requireNonNull(transactionDetector, "transactionDetector must not be null");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.transactionIdGenerator = Objects.requireNonNull(
                transactionIdGenerator, "transactionIdGenerator must not be null");
        this.policies = List.copyOf(Objects.requireNonNull(policies, "policies must not be null"));
        this.reporter = Objects.requireNonNull(reporter, "reporter must not be null");
    }

    /**
     * Ensures that the current actual transaction is observed exactly once.
     *
     * @param entryPoint candidate business entry point
     * @return current context, or empty when no actual transaction is active
     */
    public java.util.Optional<TransactionGuardContext> observe(TransactionEntryPoint entryPoint) {
        Objects.requireNonNull(entryPoint, "entryPoint must not be null");
        if (!transactionDetector.isObservationAvailable()) {
            return java.util.Optional.empty();
        }

        java.util.Optional<TransactionGuardContext> existing = contextRegistry.currentContext();
        if (existing.isPresent()) {
            return existing;
        }

        TransactionGuardContext context = new TransactionGuardContext(
                Objects.requireNonNull(transactionIdGenerator.get(), "generated transactionId must not be null"),
                clock.nanoTime(),
                entryPoint,
                Thread.currentThread().getName()
        );
        if (!contextRegistry.bindIfAbsent(context)) {
            return contextRegistry.currentContext();
        }

        try {
            TransactionSynchronizationManager.registerSynchronization(new CompletionSynchronization(context));
            return java.util.Optional.of(context);
        } catch (RuntimeException | Error registrationFailure) {
            contextRegistry.unbindIfCurrent(context);
            throw registrationFailure;
        }
    }

    private final class CompletionSynchronization implements TransactionSynchronization {

        private final TransactionGuardContext context;

        private CompletionSynchronization(TransactionGuardContext context) {
            this.context = context;
        }

        @Override
        public void suspend() {
            contextRegistry.unbindIfCurrent(context);
        }

        @Override
        public void resume() {
            contextRegistry.rebindAfterResume(context);
        }

        @Override
        public void afterCompletion(int status) {
            try {
                TransactionSnapshot snapshot = context.snapshot(clock.nanoTime(), outcome(status));
                List<TransactionGuardViolation> violations = new ArrayList<>();
                for (TransactionGuardPolicy policy : policies) {
                    violations.addAll(Objects.requireNonNull(
                            policy.evaluate(snapshot), "policy result must not be null"));
                }
                reporter.report(List.copyOf(violations));
            } finally {
                contextRegistry.unbindIfCurrent(context);
            }
        }

        private TransactionOutcome outcome(int status) {
            return switch (status) {
                case STATUS_COMMITTED -> TransactionOutcome.COMMITTED;
                case STATUS_ROLLED_BACK -> TransactionOutcome.ROLLED_BACK;
                default -> TransactionOutcome.UNKNOWN;
            };
        }
    }
}
