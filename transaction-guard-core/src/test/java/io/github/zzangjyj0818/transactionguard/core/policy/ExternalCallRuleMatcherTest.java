package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalCallRuleMatcherTest {

    @Test
    void matchesNormalizedHostAndEndpointGlobs() {
        ExternalCallRuleMatcher matcher = new ExternalCallRuleMatcher(
                List.of("*.internal"),
                List.of("metadata.example.com/health/*"),
                List.of("PAYMENTS.EXAMPLE.COM."),
                List.of("audit.example.com/events/?"));

        assertTrue(matcher.isIgnored(call("service.internal", "/orders")));
        assertTrue(matcher.isIgnored(call("metadata.example.com", "/health/ready")));
        assertTrue(matcher.isAllowed(call("payments.example.com", "/charge")));
        assertTrue(matcher.isAllowed(call("audit.example.com", "/events/1")));
        assertFalse(matcher.isAllowed(call("audit.example.com", "/events/12")));
    }

    @Test
    void ignoreTakesPrecedenceOverAllow() {
        ExternalCallRuleMatcher matcher = new ExternalCallRuleMatcher(
                List.of("shared.example.com"), List.of(),
                List.of("shared.example.com"), List.of());

        ExternalCallObservation call = call("shared.example.com", "/resource");
        assertTrue(matcher.isIgnored(call));
        assertFalse(matcher.isAllowed(call));
    }

    private static ExternalCallObservation call(String host, String path) {
        return new ExternalCallObservation(
                ExternalClientType.REST_CLIENT, "GET", host, path, 1,
                ExternalCallOutcome.SUCCESS, null);
    }
}
