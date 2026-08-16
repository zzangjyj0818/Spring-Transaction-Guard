package io.github.zzangjyj0818.transactionguard.spring.kafka;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.KafkaProducerObservation;
import io.github.zzangjyj0818.transactionguard.spring.transaction.MonotonicClock;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;

import java.util.Objects;

/** Records Kafka send invocations without topic, key, headers, or payload data. */
public final class TransactionGuardKafkaRecorder {
    private final TransactionGuardContextRegistry registry;
    private final MonotonicClock clock;

    public TransactionGuardKafkaRecorder(TransactionGuardContextRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = MonotonicClock.system();
    }

    public boolean isTransactionObserved() { return registry.currentContext().isPresent(); }
    public long nanoTime() { return clock.nanoTime(); }

    public void recordSuccess(long durationNanos) {
        record(durationNanos, ExternalCallOutcome.SUCCESS, null);
    }

    public void recordFailure(long durationNanos, Throwable failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        record(durationNanos, ExternalCallOutcome.FAILURE, failure.getClass().getName());
    }

    private void record(long durationNanos, ExternalCallOutcome outcome, String exceptionType) {
        registry.currentContext().ifPresent(context -> context.addKafkaProducerCall(
                new KafkaProducerObservation(Math.max(0, durationNanos), outcome, exceptionType)));
    }
}
