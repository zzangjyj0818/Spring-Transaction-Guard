package io.github.zzangjyj0818.transactionguard.spring.transaction;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionObservationTest {

    private final TransactionGuardContextRegistry registry = new TransactionGuardContextRegistry();

    @BeforeEach
    void initializeTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        registry.currentContext().ifPresent(registry::unbindIfCurrent);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void measuresDurationWithMonotonicClockAndCleansUp() {
        CapturingPolicy policy = new CapturingPolicy();
        TransactionObservation observation = observation(new SequenceClock(100, 250), policy);

        observation.observe(entryPoint());
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertEquals(1, policy.snapshots.size());
        assertEquals(Duration.ofNanos(150), policy.snapshots.getFirst().duration());
        assertEquals(TransactionOutcome.COMMITTED, policy.snapshots.getFirst().outcome());
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void registersOnlyOnceForTheSameTransaction() {
        TransactionObservation observation = observation(new SequenceClock(10, 20), new CapturingPolicy());

        TransactionGuardContext first = observation.observe(entryPoint()).orElseThrow();
        TransactionGuardContext second = observation.observe(new TransactionEntryPoint(
                "example.InnerService", "inner", "InnerService.inner()"
        )).orElseThrow();

        assertSame(first, second);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void cleansUpWhenReporterFails() {
        TransactionObservation observation = new TransactionObservation(
                new ActualTransactionDetector(),
                registry,
                new SequenceClock(10, 20),
                () -> "tx-test",
                List.of(new CapturingPolicy()),
                violations -> {
                    throw new IllegalStateException("reporter failure");
                },
                true
        );
        observation.observe(entryPoint());

        assertThrows(IllegalStateException.class,
                this::beforeCommit);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void suppressesReporterFailureInDefaultFailSafeMode() {
        TransactionObservation observation = new TransactionObservation(
                new ActualTransactionDetector(),
                registry,
                new SequenceClock(10, 20),
                () -> "tx-test",
                List.of(new CapturingPolicy()),
                violations -> {
                    throw new IllegalStateException("reporter failure");
                },
                false
        );
        observation.observe(entryPoint());

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void suppressesPolicyFailureInDefaultFailSafeMode() {
        TransactionObservation observation = new TransactionObservation(
                new ActualTransactionDetector(),
                registry,
                new SequenceClock(10, 20),
                () -> "tx-test",
                List.of(snapshot -> {
                    throw new IllegalStateException("policy failure");
                }),
                violations -> {
                },
                false
        );
        observation.observe(entryPoint());

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void notifiesCompletionListenerExactlyOnceWithEvaluatedViolations() {
        List<TransactionSnapshot> snapshots = new ArrayList<>();
        List<List<TransactionGuardViolation>> capturedViolations = new ArrayList<>();
        TransactionGuardPolicy policy = snapshot -> List.of(new TransactionGuardViolation(
                io.github.zzangjyj0818.transactionguard.core.model.ViolationType.LONG_TRANSACTION.code(),
                io.github.zzangjyj0818.transactionguard.core.model.ViolationType.LONG_TRANSACTION,
                io.github.zzangjyj0818.transactionguard.core.model.ViolationSeverity.WARN,
                "long transaction", snapshot, java.util.Map.of()));
        TransactionObservation observation = new TransactionObservation(
                new ActualTransactionDetector(), registry, new SequenceClock(10, 30), () -> "tx-test",
                List.of(policy), violations -> { },
                List.of((snapshot, violations) -> {
                    snapshots.add(snapshot);
                    capturedViolations.add(violations);
                }), false);

        observation.observe(entryPoint());
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertEquals(1, snapshots.size());
        assertEquals(Duration.ofNanos(20), snapshots.getFirst().duration());
        assertEquals(List.of("TG001"), capturedViolations.getFirst().stream()
                .map(TransactionGuardViolation::code).toList());
    }

    @Test
    void listenerFailureNeverChangesThrowModeReporterBehavior() {
        TransactionObservation observation = new TransactionObservation(
                new ActualTransactionDetector(), registry, new SequenceClock(10, 20), () -> "tx-test",
                List.of(new CapturingPolicy()), violations -> { },
                List.of((snapshot, violations) -> {
                    throw new IllegalStateException("metrics failure");
                }), true);
        observation.observe(entryPoint());

        beforeCommit();
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(registry.currentContext().isEmpty());
    }

    private TransactionObservation observation(MonotonicClock clock, TransactionGuardPolicy policy) {
        return new TransactionObservation(
                new ActualTransactionDetector(),
                registry,
                clock,
                () -> "tx-test",
                List.of(policy),
                violations -> {
                },
                false
        );
    }

    private void complete(int status) {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private void beforeCommit() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(synchronization -> synchronization.beforeCommit(false));
    }

    private TransactionEntryPoint entryPoint() {
        return new TransactionEntryPoint("example.OrderService", "create", "OrderService.create()");
    }

    private static final class SequenceClock implements MonotonicClock {

        private final Deque<Long> values = new ArrayDeque<>();

        private SequenceClock(long... values) {
            for (long value : values) {
                this.values.add(value);
            }
        }

        @Override
        public long nanoTime() {
            return values.removeFirst();
        }
    }

    private static final class CapturingPolicy implements TransactionGuardPolicy {

        private final List<TransactionSnapshot> snapshots = new ArrayList<>();

        @Override
        public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
            snapshots.add(snapshot);
            return List.of();
        }
    }
}
