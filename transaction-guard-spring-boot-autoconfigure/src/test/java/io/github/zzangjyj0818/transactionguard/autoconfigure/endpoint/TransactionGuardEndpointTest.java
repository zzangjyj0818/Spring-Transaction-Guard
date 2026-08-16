package io.github.zzangjyj0818.transactionguard.autoconfigure.endpoint;

import io.github.zzangjyj0818.transactionguard.autoconfigure.TransactionGuardProperties;
import io.github.zzangjyj0818.transactionguard.autoconfigure.metrics.TransactionGuardMetrics;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationSeverity;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TransactionGuardEndpointTest {

    @Test
    void returnsBoundedMetricsAndSanitizedConfiguration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TransactionGuardMetrics metrics = new TransactionGuardMetrics(registry);
        TransactionGuardProperties properties = properties();
        TransactionSnapshot snapshot = snapshot();
        metrics.onCompleted(snapshot, List.of(new TransactionGuardViolation(
                "TG002", ViolationType.EXTERNAL_HTTP_CALL_IN_TRANSACTION, ViolationSeverity.WARN,
                "safe", snapshot, Map.of())));

        TransactionGuardEndpoint.Snapshot result =
                new TransactionGuardEndpoint(registry, properties).snapshot();

        assertEquals("throw", result.configuration().violationMode());
        assertEquals(Duration.ofMillis(750).toNanos(),
                result.configuration().transactionMaxDurationNanos());
        assertEquals(Set.of("TG003"), result.configuration().disabledViolationCodes());
        assertEquals(new TransactionGuardEndpoint.RuleCounts(1, 1, 1, 1),
                result.configuration().ruleCounts());
        assertEquals(1, result.metrics().transactions().get("committed").count());
        assertEquals(1, result.metrics().violations().get("TG002"));
        assertEquals(1, result.metrics().externalHttp().get("open_feign:failure").count());
        assertEquals(Duration.ofMillis(30).toNanos(),
                result.metrics().externalHttp().get("open_feign:failure").duration().totalTimeNanos());

        String endpointValue = result.toString();
        assertFalse(endpointValue.contains("secret.internal"));
        assertFalse(endpointValue.contains("/private"));
        assertFalse(endpointValue.contains("tx-secret"));
    }

    @Test
    void returnsZeroValuesForEveryFixedDimensionBeforeObservations() {
        TransactionGuardEndpoint.Snapshot result = new TransactionGuardEndpoint(
                new SimpleMeterRegistry(), new TransactionGuardProperties()).snapshot();

        assertEquals(Set.of("committed", "rolled_back", "unknown"),
                result.metrics().transactions().keySet());
        assertEquals(Set.of("TG001", "TG002", "TG003", "TG004", "TG005"),
                result.metrics().violations().keySet());
        assertEquals(Set.of(
                        "rest_client:success", "rest_client:failure",
                        "open_feign:success", "open_feign:failure"),
                result.metrics().externalHttp().keySet());
        assertEquals(0, result.metrics().transactions().get("committed").count());
    }

    private static TransactionGuardProperties properties() {
        TransactionGuardProperties properties = new TransactionGuardProperties();
        properties.getTransaction().setMaxDuration(Duration.ofMillis(750));
        properties.getExternalCall().setSlowThreshold(Duration.ofMillis(250));
        properties.getExternalCall().setIgnoreHosts(List.of("secret.internal"));
        properties.getExternalCall().setIgnoreEndpoints(List.of("secret.internal/private/*"));
        properties.getExternalCall().setAllowHosts(List.of("trusted.internal"));
        properties.getExternalCall().setAllowEndpoints(List.of("trusted.internal/public/*"));
        properties.getViolation().setMode(TransactionGuardProperties.Mode.THROW);
        properties.getViolation().setDisabledCodes(Set.of(TransactionGuardProperties.ViolationCode.TG003));
        return properties;
    }

    private static TransactionSnapshot snapshot() {
        return new TransactionSnapshot(
                "tx-secret",
                new TransactionEntryPoint("example.Service", "call", "Service.call()"),
                Duration.ofMillis(100),
                TransactionOutcome.COMMITTED,
                List.of(new ExternalCallObservation(
                        ExternalClientType.OPEN_FEIGN, "POST", "secret.internal", "/private",
                        Duration.ofMillis(30).toNanos(), ExternalCallOutcome.FAILURE,
                        IllegalStateException.class.getName())));
    }
}
