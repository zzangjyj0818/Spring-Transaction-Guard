package io.github.zzangjyj0818.transactionguard.spring.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.policy.ExternalHttpCallPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.SlowExternalHttpCallPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.spring.aop.TransactionGuardAspect;
import io.github.zzangjyj0818.transactionguard.spring.transaction.ActualTransactionDetector;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestClientObservationIntegrationTest {

    private static HttpServer server;
    private static String baseUrl;
    private static AnnotationConfigApplicationContext applicationContext;
    private static HttpScenarioService service;
    private static CapturingPolicy policy;
    private static CapturingReporter reporter;
    private static TransactionGuardContextRegistry registry;

    @BeforeAll
    static void startInfrastructure() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", RestClientObservationIntegrationTest::respond);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        applicationContext = new AnnotationConfigApplicationContext(TestConfiguration.class);
        service = applicationContext.getBean(HttpScenarioService.class);
        policy = applicationContext.getBean(CapturingPolicy.class);
        reporter = applicationContext.getBean(CapturingReporter.class);
        registry = applicationContext.getBean(TransactionGuardContextRegistry.class);
    }

    @AfterAll
    static void stopInfrastructure() {
        applicationContext.close();
        server.stop(0);
    }

    @BeforeEach
    void resetCaptures() {
        policy.clear();
        reporter.clear();
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void successfulCallInsideTransactionProducesTg002() {
        assertEquals("ok", service.get(baseUrl + "/ok?token=SECRET"));

        TransactionSnapshot snapshot = onlySnapshot();
        ExternalCallObservation call = snapshot.externalCalls().getFirst();
        assertEquals("GET", call.httpMethod());
        assertEquals("localhost", call.host());
        assertEquals("/ok", call.path());
        assertEquals(ExternalCallOutcome.SUCCESS, call.outcome());
        assertFalse(call.path().contains("SECRET"));
        assertTrue(reporter.codes().contains("TG002"));
    }

    @Test
    void queryHeaderAndBodySecretsAreNotCaptured() {
        service.postWithSecrets(
                baseUrl + "/ok?access_token=query-secret",
                "Bearer header-secret",
                "{\"password\":\"body-secret\"}"
        );
        String snapshotText = onlySnapshot().toString();

        assertFalse(snapshotText.contains("query-secret"));
        assertFalse(snapshotText.contains("header-secret"));
        assertFalse(snapshotText.contains("body-secret"));
        assertEquals("/ok", onlySnapshot().externalCalls().getFirst().path());
    }

    @Test
    void callOutsideTransactionIsNotObserved() {
        assertEquals("ok", service.getWithoutTransaction(baseUrl + "/ok?token=SECRET"));
        assertTrue(policy.snapshots().isEmpty());
        assertTrue(reporter.violations().isEmpty());
    }

    @Test
    void slowCallProducesTg002AndTg003() {
        assertEquals("slow", service.get(baseUrl + "/slow"));
        assertEquals(List.of("TG002", "TG003"), reporter.codes());
        assertTrue(onlySnapshot().externalCalls().getFirst().durationNanos() > Duration.ofMillis(150).toNanos());
    }

    @Test
    void httpErrorIsObservedAndOriginalRestClientExceptionIsPreserved() {
        RestClientResponseException failure = assertThrows(
                RestClientResponseException.class,
                () -> service.get(baseUrl + "/error")
        );

        assertEquals(503, failure.getStatusCode().value());
        ExternalCallObservation call = onlySnapshot().externalCalls().getFirst();
        assertEquals(ExternalCallOutcome.FAILURE, call.outcome());
        assertEquals(List.of("TG002"), reporter.codes());
    }

    @Test
    void networkFailureIsObservedWithoutReplacingOriginalException() throws IOException {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }

        assertThrows(ResourceAccessException.class,
                () -> service.get("http://localhost:" + unavailablePort + "/unavailable?token=SECRET"));

        ExternalCallObservation call = onlySnapshot().externalCalls().getFirst();
        assertEquals(ExternalCallOutcome.FAILURE, call.outcome());
        assertNotNull(call.exceptionType());
        assertEquals("/unavailable", call.path());
    }

    @Test
    void notSupportedCallIsNotAttachedToSuspendedTransaction() {
        service.outerWithNotSupportedCall(baseUrl + "/ok?token=SECRET");

        assertTrue(onlySnapshot().externalCalls().isEmpty());
        assertTrue(reporter.violations().isEmpty());
    }

    @Test
    void concurrentTransactionsDoNotMixHttpCalls() throws Exception {
        int count = 100;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int index = 0; index < count; index++) {
                int callIndex = index;
                executor.submit(() -> {
                    await(start);
                    service.get(baseUrl + "/call/" + callIndex + "?secret=" + callIndex);
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(count, policy.snapshots().size());
        Set<String> transactionIds = ConcurrentHashMap.newKeySet();
        Set<String> paths = ConcurrentHashMap.newKeySet();
        for (TransactionSnapshot snapshot : policy.snapshots()) {
            transactionIds.add(snapshot.transactionId());
            assertEquals(1, snapshot.externalCalls().size());
            paths.add(snapshot.externalCalls().getFirst().path());
        }
        assertEquals(count, transactionIds.size());
        assertEquals(count, paths.size());
        assertTrue(registry.currentContext().isEmpty());
    }

    private TransactionSnapshot onlySnapshot() {
        assertEquals(1, policy.snapshots().size());
        return policy.snapshots().getFirst();
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int status = path.equals("/error") ? 503 : 200;
        String response = path.equals("/slow") ? "slow" : path.startsWith("/call/") ? path : "ok";
        if (path.equals("/slow")) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 1)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }

        @Bean
        TransactionGuardContextRegistry contextRegistry() {
            return new TransactionGuardContextRegistry();
        }

        @Bean
        CapturingPolicy policy() {
            return new CapturingPolicy(List.of(
                    new ExternalHttpCallPolicy(),
                    new SlowExternalHttpCallPolicy(Duration.ofMillis(150))
            ));
        }

        @Bean
        CapturingReporter reporter() {
            return new CapturingReporter();
        }

        @Bean
        TransactionObservation observation(
                TransactionGuardContextRegistry registry,
                CapturingPolicy policy,
                CapturingReporter reporter
        ) {
            return new TransactionObservation(
                    new ActualTransactionDetector(), registry, List.of(policy), reporter);
        }

        @Bean
        TransactionGuardAspect aspect(TransactionObservation observation) {
            return new TransactionGuardAspect(observation);
        }

        @Bean
        RestClient restClient(TransactionGuardContextRegistry registry) {
            TransactionGuardHttpRecorder recorder = new TransactionGuardHttpRecorder(registry);
            TransactionGuardHttpInterceptor interceptor = new TransactionGuardHttpInterceptor(recorder);
            RestClient.Builder builder = RestClient.builder();
            new TransactionGuardRestClientConfigurer(interceptor).configure(builder);
            return builder.build();
        }

        @Bean
        NotSupportedHttpService notSupportedHttpService(RestClient restClient) {
            return new NotSupportedHttpService(restClient);
        }

        @Bean
        HttpScenarioService httpScenarioService(RestClient restClient, NotSupportedHttpService nested) {
            return new HttpScenarioService(restClient, nested);
        }
    }

    static class HttpScenarioService {
        private final RestClient client;
        private final NotSupportedHttpService nested;

        HttpScenarioService(RestClient client, NotSupportedHttpService nested) {
            this.client = client;
            this.nested = nested;
        }

        @Transactional
        String get(String url) {
            return client.get().uri(url).retrieve().body(String.class);
        }

        String getWithoutTransaction(String url) {
            return client.get().uri(url).retrieve().body(String.class);
        }

        @Transactional
        void postWithSecrets(String url, String authorization, String body) {
            client.post()
                    .uri(url)
                    .header("Authorization", authorization)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        }

        @Transactional
        void outerWithNotSupportedCall(String url) {
            nested.get(url);
        }
    }

    static class NotSupportedHttpService {
        private final RestClient client;

        NotSupportedHttpService(RestClient client) {
            this.client = client;
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        String get(String url) {
            return client.get().uri(url).retrieve().body(String.class);
        }

    }

    static final class CapturingPolicy implements TransactionGuardPolicy {
        private final List<TransactionGuardPolicy> delegates;
        private final List<TransactionSnapshot> snapshots = new CopyOnWriteArrayList<>();

        CapturingPolicy(List<TransactionGuardPolicy> delegates) {
            this.delegates = List.copyOf(delegates);
        }

        @Override
        public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
            snapshots.add(snapshot);
            return delegates.stream().flatMap(delegate -> delegate.evaluate(snapshot).stream()).toList();
        }

        List<TransactionSnapshot> snapshots() {
            return List.copyOf(snapshots);
        }

        void clear() {
            snapshots.clear();
        }
    }

    static final class CapturingReporter implements TransactionGuardReporter {
        private final List<TransactionGuardViolation> violations = new CopyOnWriteArrayList<>();

        @Override
        public void report(List<TransactionGuardViolation> violations) {
            this.violations.addAll(violations);
        }

        List<TransactionGuardViolation> violations() {
            return List.copyOf(violations);
        }

        List<String> codes() {
            return violations.stream().map(TransactionGuardViolation::code).toList();
        }

        void clear() {
            violations.clear();
        }
    }
}
