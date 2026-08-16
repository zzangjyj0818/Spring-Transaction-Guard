package io.github.zzangjyj0818.transactionguard.core.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.entryPoint;
import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.externalCall;
import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreModelTest {

    @Test
    void violationTypesExposeStableCodes() {
        assertEquals("TG001", ViolationType.LONG_TRANSACTION.code());
        assertEquals("TG002", ViolationType.EXTERNAL_HTTP_CALL_IN_TRANSACTION.code());
        assertEquals("TG003", ViolationType.SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION.code());
    }

    @Test
    void entryPointRejectsBlankValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionEntryPoint(" ", "create", "OrderService.create()"));
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionEntryPoint("OrderService", " ", "OrderService.create()"));
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionEntryPoint("OrderService", "create", " "));
    }

    @Test
    void externalCallRejectsNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> externalCall(-1));
    }

    @Test
    void redisOperationRejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new RedisOperationObservation(
                RedisClientType.REDIS_TEMPLATE,
                RedisCommandCategory.READ,
                -1,
                ExternalCallOutcome.SUCCESS,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new RedisOperationObservation(
                RedisClientType.REDIS_TEMPLATE,
                RedisCommandCategory.READ,
                1,
                ExternalCallOutcome.FAILURE,
                " "
        ));
    }

    @Test
    void snapshotDefensivelyCopiesExternalCalls() {
        List<ExternalCallObservation> calls = new ArrayList<>();
        calls.add(externalCall(100));

        TransactionSnapshot snapshot = snapshot(Duration.ofNanos(100), calls);
        calls.clear();

        assertEquals(1, snapshot.externalCalls().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.externalCalls().add(externalCall(200)));
    }

    @Test
    void snapshotDefensivelyCopiesRedisOperations() {
        List<RedisOperationObservation> operations = new ArrayList<>();
        operations.add(new RedisOperationObservation(
                RedisClientType.REDIS_TEMPLATE,
                RedisCommandCategory.WRITE,
                100,
                ExternalCallOutcome.SUCCESS,
                null
        ));

        TransactionSnapshot snapshot = new TransactionSnapshot(
                "tx", entryPoint(), Duration.ofNanos(100), TransactionOutcome.COMMITTED,
                List.of(), operations
        );
        operations.clear();

        assertEquals(1, snapshot.redisOperations().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.redisOperations().clear());
    }

    @Test
    void snapshotRejectsNegativeDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> snapshot(Duration.ofNanos(-1), List.of()));
    }

    @Test
    void violationRequiresCodeMatchingItsType() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionGuardViolation(
                "TG999",
                ViolationType.LONG_TRANSACTION,
                ViolationSeverity.WARN,
                "message",
                snapshot(Duration.ZERO, List.of()),
                Map.of()
        ));
    }

    @Test
    void violationDefensivelyCopiesAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("thresholdNanos", 10L);

        TransactionGuardViolation violation = new TransactionGuardViolation(
                "TG001",
                ViolationType.LONG_TRANSACTION,
                ViolationSeverity.WARN,
                "message",
                snapshot(Duration.ofNanos(11), List.of()),
                attributes
        );
        attributes.clear();

        assertEquals(10L, violation.attributes().get("thresholdNanos"));
        assertThrows(UnsupportedOperationException.class,
                () -> violation.attributes().put("durationNanos", 11L));
    }

    @Test
    void modelRejectsNullRequiredValues() {
        assertThrows(NullPointerException.class,
                () -> new TransactionSnapshot("tx", entryPoint(), Duration.ZERO, null, List.of()));
        assertThrows(NullPointerException.class,
                () -> new TransactionSnapshot("tx", entryPoint(), Duration.ZERO,
                        TransactionOutcome.COMMITTED, null));
        assertThrows(NullPointerException.class,
                () -> new TransactionSnapshot("tx", entryPoint(), Duration.ZERO,
                        TransactionOutcome.COMMITTED, List.of(), null));
    }
}
