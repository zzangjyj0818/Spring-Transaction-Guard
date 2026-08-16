package io.github.zzangjyj0818.transactionguard.core.policy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongTransactionPolicyTest {

    private final LongTransactionPolicy policy = new LongTransactionPolicy(Duration.ofSeconds(2));

    @Test
    void doesNotViolateBelowOrAtThreshold() {
        assertTrue(policy.evaluate(snapshot(Duration.ofSeconds(2).minusNanos(1), List.of())).isEmpty());
        assertTrue(policy.evaluate(snapshot(Duration.ofSeconds(2), List.of())).isEmpty());
    }

    @Test
    void producesTg001AboveThreshold() {
        var violations = policy.evaluate(snapshot(Duration.ofSeconds(2).plusNanos(1), List.of()));

        assertEquals(1, violations.size());
        assertEquals("TG001", violations.getFirst().code());
        assertEquals(2_000_000_001L, violations.getFirst().attributes().get("durationNanos"));
        assertEquals(2_000_000_000L, violations.getFirst().attributes().get("thresholdNanos"));
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> new LongTransactionPolicy(Duration.ofNanos(-1)));
    }
}
