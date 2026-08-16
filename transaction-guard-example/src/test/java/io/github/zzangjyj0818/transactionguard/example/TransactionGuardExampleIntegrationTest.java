package io.github.zzangjyj0818.transactionguard.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "transaction-guard.transaction.max-duration=150ms",
        "transaction-guard.external-call.slow-threshold=100ms"
})
class TransactionGuardExampleIntegrationTest {

    private static final HttpServer SERVER = startServer();

    @Autowired
    private TransactionRiskScenarios scenarios;

    @Autowired
    private CapturingReporter reporter;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void exampleProperties(DynamicPropertyRegistry registry) {
        registry.add("example.remote-base-url", () -> "http://localhost:" + SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void clearViolations() throws java.sql.SQLException {
        try (var ignored = dataSource.getConnection()) {
            // Warm the pool so its one-time startup cost is not attributed to a scenario.
        }
        reporter.clear();
    }

    @Test
    void starterProducesTg001FromAnActualTransaction() {
        assertEquals("TG001 scenario completed", scenarios.longTransaction());
        assertEquals(List.of("TG001"), reporter.codes());
    }

    @Test
    void bootManagedRestClientProducesTg002() {
        assertEquals("fast response", scenarios.externalCallInTransaction());
        assertEquals(List.of("TG002"), reporter.codes());
    }

    @Test
    void bootManagedRestClientProducesTg002AndTg003ForSlowCall() {
        assertEquals("slow response", scenarios.slowExternalCallInTransaction());
        assertEquals(List.of("TG001", "TG002", "TG003"), reporter.codes());
    }

    private static void respond(HttpExchange exchange, String body, long delayMillis) throws IOException {
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/remote/fast", exchange -> respond(exchange, "fast response", 0));
            server.createContext("/remote/slow", exchange -> respond(exchange, "slow response", 200));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start example HTTP server", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {
        @Bean
        CapturingReporter transactionGuardReporter() {
            return new CapturingReporter();
        }
    }

    static final class CapturingReporter implements TransactionGuardReporter {
        private final List<TransactionGuardViolation> violations = new CopyOnWriteArrayList<>();

        @Override
        public void report(List<TransactionGuardViolation> violations) {
            this.violations.addAll(violations);
        }

        List<String> codes() {
            return violations.stream().map(TransactionGuardViolation::code).sorted().toList();
        }

        void clear() {
            violations.clear();
        }
    }
}
