package io.github.zzangjyj0818.transactionguard.example;

import io.github.zzangjyj0818.transactionguard.autoconfigure.metrics.TransactionGuardMetrics;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionGuardObservabilityIntegrationTest {

    private final RestClient client;
    private final TransactionGuardMetrics metrics;

    @Autowired
    TransactionGuardObservabilityIntegrationTest(
            @Value("${local.server.port}") int port,
            TransactionGuardMetrics metrics
    ) {
        this.client = RestClient.create("http://localhost:" + port);
        this.metrics = metrics;
    }

    @Test
    void exposesPrometheusMetricsAndReadOnlyGuardSnapshot() {
        client.get().uri("/guard/tg001").retrieve().toBodilessEntity();
        metrics.onCompleted(new TransactionSnapshot(
                "smoke-test",
                new TransactionEntryPoint("Example", "observe", "Example.observe()"),
                Duration.ofMillis(10),
                TransactionOutcome.COMMITTED,
                List.of(new ExternalCallObservation(
                        ExternalClientType.REST_CLIENT, "GET", "example.internal", "/resource",
                        Duration.ofMillis(5).toNanos(), ExternalCallOutcome.SUCCESS, null))),
                List.of());

        String prometheus = client.get().uri("/actuator/prometheus")
                .retrieve().body(String.class);
        String endpoint = client.get().uri("/actuator/transactionguard")
                .retrieve().body(String.class);

        assertTrue(prometheus.contains("transaction_guard_transaction_duration_seconds_count"));
        assertTrue(prometheus.contains("transaction_guard_violation_total"));
        assertTrue(prometheus.contains("transaction_guard_external_http_duration_seconds_count"));
        assertTrue(prometheus.contains("transaction_guard_external_http_total"));
        assertTrue(endpoint.contains("transactionMaxDurationNanos"));
        assertTrue(endpoint.contains("transactions"));
        assertTrue(endpoint.contains("TG001"));
    }
}
