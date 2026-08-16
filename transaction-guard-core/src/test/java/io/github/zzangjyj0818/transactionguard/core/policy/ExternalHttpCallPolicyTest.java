package io.github.zzangjyj0818.transactionguard.core.policy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.externalCall;
import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalHttpCallPolicyTest {

    private final ExternalHttpCallPolicy policy = new ExternalHttpCallPolicy();

    @Test
    void returnsNoViolationWithoutCalls() {
        assertTrue(policy.evaluate(snapshot(Duration.ofSeconds(1), List.of())).isEmpty());
    }

    @Test
    void producesOneTg002PerCall() {
        var violations = policy.evaluate(snapshot(
                Duration.ofSeconds(2),
                List.of(externalCall(10), externalCall(20))
        ));

        assertEquals(2, violations.size());
        assertTrue(violations.stream().allMatch(violation -> violation.code().equals("TG002")));
        assertEquals("payments.example.com", violations.getFirst().attributes().get("host"));
        assertEquals("/v1/payments", violations.getFirst().attributes().get("path"));
    }
}
