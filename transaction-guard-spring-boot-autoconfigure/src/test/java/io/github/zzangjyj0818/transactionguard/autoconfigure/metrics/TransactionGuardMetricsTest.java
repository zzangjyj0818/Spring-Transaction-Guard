package io.github.zzangjyj0818.transactionguard.autoconfigure.metrics;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationSeverity;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransactionGuardMetricsTest {

    @Test
    void recordsTransactionViolationsAndExternalCallsWithSafeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TransactionGuardMetrics metrics = new TransactionGuardMetrics(registry);
        TransactionSnapshot snapshot = snapshot();

        metrics.onCompleted(snapshot, List.of(
                violation(ViolationType.LONG_TRANSACTION, snapshot),
                violation(ViolationType.EXTERNAL_HTTP_CALL_IN_TRANSACTION, snapshot),
                violation(ViolationType.SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION, snapshot)));

        Timer transactionTimer = registry.find(TransactionGuardMetrics.TRANSACTION_DURATION)
                .tag("outcome", "committed").timer();
        assertNotNull(transactionTimer);
        assertEquals(1, transactionTimer.count());
        assertEquals(Duration.ofMillis(250).toNanos(), transactionTimer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS));

        assertEquals(1, counter(registry, TransactionGuardMetrics.VIOLATION_TOTAL, "code", "TG001").count());
        assertEquals(1, counter(registry, TransactionGuardMetrics.VIOLATION_TOTAL, "code", "TG002").count());
        assertEquals(1, counter(registry, TransactionGuardMetrics.VIOLATION_TOTAL, "code", "TG003").count());

        Timer externalTimer = registry.find(TransactionGuardMetrics.EXTERNAL_HTTP_DURATION)
                .tags("client_type", "rest_client", "outcome", "success").timer();
        assertNotNull(externalTimer);
        assertEquals(1, externalTimer.count());
        assertEquals(Duration.ofMillis(40).toNanos(), externalTimer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS));
        assertEquals(1, registry.find(TransactionGuardMetrics.EXTERNAL_HTTP_TOTAL)
                .tags("client_type", "rest_client", "outcome", "success").counter().count());

        assertNull(registry.find(TransactionGuardMetrics.TRANSACTION_DURATION).tagKeys("transactionId").meter());
        assertNull(registry.find(TransactionGuardMetrics.EXTERNAL_HTTP_TOTAL).tagKeys("host", "path").meter());
    }

    @Test
    void accumulatesMultipleCallsByClientTypeAndOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TransactionGuardMetrics metrics = new TransactionGuardMetrics(registry);
        TransactionSnapshot first = snapshot();
        TransactionSnapshot second = new TransactionSnapshot(
                "tx-2", first.entryPoint(), Duration.ofMillis(10), TransactionOutcome.ROLLED_BACK,
                List.of(new ExternalCallObservation(
                        ExternalClientType.OPEN_FEIGN, "POST", "orders.internal", "/orders",
                        Duration.ofMillis(5).toNanos(), ExternalCallOutcome.FAILURE,
                        IllegalStateException.class.getName())));

        metrics.onCompleted(first, List.of());
        metrics.onCompleted(second, List.of());

        assertEquals(1, registry.find(TransactionGuardMetrics.TRANSACTION_DURATION)
                .tag("outcome", "committed").timer().count());
        assertEquals(1, registry.find(TransactionGuardMetrics.TRANSACTION_DURATION)
                .tag("outcome", "rolled_back").timer().count());
        assertEquals(1, registry.find(TransactionGuardMetrics.EXTERNAL_HTTP_TOTAL)
                .tags("client_type", "open_feign", "outcome", "failure").counter().count());
    }

    private static Counter counter(
            SimpleMeterRegistry registry, String name, String tagKey, String tagValue
    ) {
        Counter counter = registry.find(name).tag(tagKey, tagValue).counter();
        assertNotNull(counter);
        return counter;
    }

    private static TransactionSnapshot snapshot() {
        return new TransactionSnapshot(
                "tx-sensitive-id",
                new TransactionEntryPoint("example.OrderService", "create", "OrderService.create()"),
                Duration.ofMillis(250),
                TransactionOutcome.COMMITTED,
                List.of(new ExternalCallObservation(
                        ExternalClientType.REST_CLIENT, "GET", "secret.internal", "/private/orders",
                        Duration.ofMillis(40).toNanos(), ExternalCallOutcome.SUCCESS, null)));
    }

    private static TransactionGuardViolation violation(
            ViolationType type, TransactionSnapshot snapshot
    ) {
        return new TransactionGuardViolation(
                type.code(), type, ViolationSeverity.WARN, "safe message", snapshot, Map.of());
    }
}
