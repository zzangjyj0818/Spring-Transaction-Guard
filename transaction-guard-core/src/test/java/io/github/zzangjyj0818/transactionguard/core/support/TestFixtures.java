package io.github.zzangjyj0818.transactionguard.core.support;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationSeverity;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static TransactionEntryPoint entryPoint() {
        return new TransactionEntryPoint(
                "com.example.OrderService",
                "createOrder",
                "OrderService.createOrder(OrderCommand)"
        );
    }

    public static ExternalCallObservation externalCall(long durationNanos) {
        return new ExternalCallObservation(
                ExternalClientType.REST_CLIENT,
                "POST",
                "payments.example.com",
                "/v1/payments",
                durationNanos,
                ExternalCallOutcome.SUCCESS,
                null
        );
    }

    public static TransactionSnapshot snapshot(Duration duration, List<ExternalCallObservation> calls) {
        return new TransactionSnapshot(
                "tx-123",
                entryPoint(),
                duration,
                TransactionOutcome.COMMITTED,
                calls
        );
    }

    public static TransactionGuardViolation violation(ViolationType type) {
        return new TransactionGuardViolation(
                type.code(),
                type,
                ViolationSeverity.WARN,
                "test violation",
                snapshot(Duration.ofSeconds(3), List.of()),
                Map.of()
        );
    }
}
