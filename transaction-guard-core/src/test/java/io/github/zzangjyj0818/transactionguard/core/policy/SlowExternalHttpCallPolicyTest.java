package io.github.zzangjyj0818.transactionguard.core.policy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.externalCall;
import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlowExternalHttpCallPolicyTest {

    private final SlowExternalHttpCallPolicy policy = new SlowExternalHttpCallPolicy(Duration.ofSeconds(1));

    @Test
    void onlyProducesTg003AboveThreshold() {
        var violations = policy.evaluate(snapshot(
                Duration.ofSeconds(3),
                List.of(
                        externalCall(999_999_999L),
                        externalCall(1_000_000_000L),
                        externalCall(1_000_000_001L),
                        externalCall(2_000_000_000L)
                )
        ));

        assertEquals(2, violations.size());
        assertEquals("TG003", violations.getFirst().code());
        assertEquals(1_000_000_000L, violations.getFirst().attributes().get("thresholdNanos"));
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlowExternalHttpCallPolicy(Duration.ofNanos(-1)));
    }

    @Test
    void skipsSlowCallsRejectedByCandidatePredicate() {
        SlowExternalHttpCallPolicy filtered = new SlowExternalHttpCallPolicy(
                Duration.ZERO, call -> false);

        assertEquals(0, filtered.evaluate(snapshot(
                Duration.ofSeconds(1), List.of(externalCall(1)))).size());
    }
}
