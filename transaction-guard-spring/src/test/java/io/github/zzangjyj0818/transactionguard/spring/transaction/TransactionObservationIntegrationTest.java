package io.github.zzangjyj0818.transactionguard.spring.transaction;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.policy.LongTransactionPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.spring.aop.TransactionGuardAspect;
import io.github.zzangjyj0818.transactionguard.spring.jdbc.TransactionGuardJdbcAspect;
import io.github.zzangjyj0818.transactionguard.spring.jdbc.TransactionGuardJdbcRecorder;
import io.github.zzangjyj0818.transactionguard.spring.redis.TransactionGuardRedisAspect;
import io.github.zzangjyj0818.transactionguard.spring.redis.TransactionGuardRedisRecorder;
import io.github.zzangjyj0818.transactionguard.spring.kafka.TransactionGuardKafkaAspect;
import io.github.zzangjyj0818.transactionguard.spring.kafka.TransactionGuardKafkaRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;

import javax.sql.DataSource;
import java.time.Duration;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.CallableStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionObservationIntegrationTest {

    private static AnnotationConfigApplicationContext applicationContext;
    private static ScenarioService scenarioService;
    private static CapturingPolicy policy;
    private static CapturingReporter reporter;
    private static TransactionGuardContextRegistry registry;

    @BeforeAll
    static void startContext() {
        applicationContext = new AnnotationConfigApplicationContext(TestConfiguration.class);
        scenarioService = applicationContext.getBean(ScenarioService.class);
        policy = applicationContext.getBean(CapturingPolicy.class);
        reporter = applicationContext.getBean(CapturingReporter.class);
        registry = applicationContext.getBean(TransactionGuardContextRegistry.class);
    }

    @AfterAll
    static void closeContext() {
        applicationContext.close();
    }

    @BeforeEach
    void resetCaptures() {
        policy.clear();
        reporter.clear();
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void createsContextOnlyInsideActualTransaction() {
        assertTrue(AopUtils.isAopProxy(scenarioService));
        assertFalse(scenarioService.withoutTransaction());
        assertTrue(scenarioService.contextPresentInTransaction());

        assertEquals(1, policy.snapshots().size());
        assertEquals(TransactionOutcome.COMMITTED, policy.snapshots().getFirst().outcome());
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void recordsRollbackAndCleansContextAfterException() {
        assertThrows(IllegalStateException.class, scenarioService::rollsBack);

        assertEquals(1, policy.snapshots().size());
        assertEquals(TransactionOutcome.ROLLED_BACK, policy.snapshots().getFirst().outcome());
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void requiredNestedInvocationReusesOneContext() {
        scenarioService.outerRequired();

        assertEquals(1, policy.snapshots().size());
        assertTrue(policy.snapshots().getFirst().entryPoint().methodName().equals("outerRequired"));
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void requiresNewSuspendsAndRestoresOuterContext() {
        NestedIds ids = scenarioService.outerRequiresNew();

        assertEquals(ids.outerBefore(), ids.outerAfter());
        assertNotEquals(ids.outerBefore(), ids.inner());
        assertEquals(2, policy.snapshots().size());
        assertEquals(2, policy.snapshots().stream().map(TransactionSnapshot::transactionId).distinct().count());
        assertTrue(policy.snapshots().stream()
                .allMatch(snapshot -> snapshot.outcome() == TransactionOutcome.COMMITTED));
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void notSupportedSuspendsContextAndThenRestoresIt() {
        NotSupportedState state = scenarioService.outerNotSupported();

        assertTrue(state.outerBefore());
        assertFalse(state.inner());
        assertTrue(state.outerAfter());
        assertEquals(1, policy.snapshots().size());
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void ioObservationsAreIsolatedAcrossRequiresNew() {
        scenarioService.outerIoRequiresNew();

        assertEquals(2, policy.snapshots().size());
        TransactionSnapshot outer = policy.snapshots().stream()
                .filter(snapshot -> snapshot.entryPoint().methodName().equals("outerIoRequiresNew"))
                .findFirst().orElseThrow();
        TransactionSnapshot inner = policy.snapshots().stream()
                .filter(snapshot -> snapshot.entryPoint().methodName().equals("requiresNewIo"))
                .findFirst().orElseThrow();
        assertEquals(1, outer.jdbcQueries().queryCount());
        assertEquals(0, outer.redisOperations().size());
        assertEquals(1, inner.redisOperations().size());
        assertEquals(0, inner.jdbcQueries().queryCount());
    }

    @Test
    void notSupportedIoIsNotAttachedToTheOuterTransaction() {
        scenarioService.outerWithNotSupportedIo();

        TransactionSnapshot outer = policy.snapshots().getFirst();
        assertEquals(2, outer.jdbcQueries().queryCount());
        assertEquals(0, outer.redisOperations().size());
        assertEquals(0, outer.kafkaProducerCalls().size());
    }

    @Test
    void evaluatesLongTransactionPolicyAndReportsTg001() {
        scenarioService.contextPresentInTransaction();

        assertEquals(1, reporter.violations().size());
        assertEquals("TG001", reporter.violations().getFirst().code());
    }

    @Test
    void recordsEachJdbcExecutionExactlyOnceWithoutSqlData() {
        scenarioService.executeQueries();

        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(3, snapshot.jdbcQueries().queryCount());
        assertEquals(0, snapshot.jdbcQueries().failedQueryCount());
        assertTrue(snapshot.jdbcQueries().totalDurationNanos() >= 0);
        assertFalse(snapshot.toString().contains("top-secret"));
    }

    @Test
    void recordsJdbcFailureAndPreservesTheOriginalExceptionType() {
        assertThrows(org.springframework.jdbc.BadSqlGrammarException.class,
                scenarioService::executeFailingQuery);

        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(1, snapshot.jdbcQueries().queryCount());
        assertEquals(1, snapshot.jdbcQueries().failedQueryCount());
        assertEquals(TransactionOutcome.ROLLED_BACK, snapshot.outcome());
        assertFalse(snapshot.toString().contains("missing_secret_table"));
    }

    @Test
    void jdbcUnwrapCannotSilentlyBypassGuardInstrumentation() {
        scenarioService.executeThroughUnwrappedConnection();

        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(1, snapshot.jdbcQueries().queryCount());
        assertEquals(0, snapshot.jdbcQueries().failedQueryCount());
    }

    @Test
    void countsBatchAndCallableExecutionOncePerExecuteMethod() {
        scenarioService.executeBatchAndCallable();

        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(2, snapshot.jdbcQueries().queryCount());
        assertEquals(0, snapshot.jdbcQueries().failedQueryCount());
    }

    @Test
    void ioOutsideTransactionDoesNotCreateOrLeakAContext() {
        scenarioService.executeIoWithoutTransaction();

        assertEquals(0, policy.snapshots().size());
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void observesRedisOperationsThroughTheRealSpringAopPointcut() {
        scenarioService.executeRedisOperations();

        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(2, snapshot.redisOperations().size());
        assertEquals("READ", snapshot.redisOperations().get(0).commandCategory().name());
        assertEquals("WRITE", snapshot.redisOperations().get(1).commandCategory().name());
        assertTrue(snapshot.redisOperations().stream()
                .allMatch(operation -> operation.exceptionType() == null));
        assertFalse(snapshot.toString().contains("secret-key"));
        assertFalse(snapshot.toString().contains("secret-value"));
    }

    @Test
    void recordsRedisFailureAndPreservesTheOriginalException() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, scenarioService::executeFailingRedisOperation);

        assertEquals("redis failure with secret-value", failure.getMessage());
        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(1, snapshot.redisOperations().size());
        assertEquals(IllegalStateException.class.getName(),
                snapshot.redisOperations().getFirst().exceptionType());
        assertFalse(snapshot.toString().contains("redis failure with secret-value"));
    }

    @Test
    void recordsKafkaFutureCompletionWithoutPayloadData() {
        scenarioService.executeKafkaSend();

        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(1, snapshot.kafkaProducerCalls().size());
        assertEquals("SUCCESS", snapshot.kafkaProducerCalls().getFirst().outcome().name());
        assertFalse(snapshot.toString().contains("secret-topic"));
        assertFalse(snapshot.toString().contains("secret-payload"));
    }

    @Test
    void recordsKafkaFutureFailureWithoutReplacingTheReturnedFailure() {
        CompletionException failure = assertThrows(
                CompletionException.class, scenarioService::executeFailingKafkaSend);

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(1, snapshot.kafkaProducerCalls().size());
        assertEquals("FAILURE", snapshot.kafkaProducerCalls().getFirst().outcome().name());
        assertEquals(IllegalStateException.class.getName(),
                snapshot.kafkaProducerCalls().getFirst().exceptionType());
        assertFalse(snapshot.toString().contains("secret kafka failure"));
    }

    @Test
    void kafkaCompletionAfterTransactionIsNotAddedRetroactively() {
        CompletableFuture<SendResult<String, String>> future = scenarioService.executePendingKafkaSend();

        TransactionSnapshot snapshot = policy.snapshots().getFirst();
        assertEquals(0, snapshot.kafkaProducerCalls().size());
        future.complete(null);
        assertEquals(0, snapshot.kafkaProducerCalls().size());
        assertTrue(registry.currentContext().isEmpty());
    }

    @Test
    void isolatesConcurrentTransactions() throws Exception {
        int transactionCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < transactionCount; i++) {
                executor.submit(() -> {
                    await(start);
                    scenarioService.executeSingleQuery();
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(transactionCount, policy.snapshots().size());
        Set<String> transactionIds = ConcurrentHashMap.newKeySet();
        policy.snapshots().forEach(snapshot -> transactionIds.add(snapshot.transactionId()));
        assertEquals(transactionCount, transactionIds.size());
        assertTrue(policy.snapshots().stream()
                .allMatch(snapshot -> snapshot.jdbcQueries().queryCount() == 1));
        assertTrue(registry.currentContext().isEmpty());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    record NestedIds(String outerBefore, String inner, String outerAfter) {
    }

    record NotSupportedState(boolean outerBefore, boolean inner, boolean outerAfter) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 1)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }

        @Bean
        ActualTransactionDetector actualTransactionDetector() {
            return new ActualTransactionDetector();
        }

        @Bean
        TransactionGuardContextRegistry transactionGuardContextRegistry() {
            return new TransactionGuardContextRegistry();
        }

        @Bean
        CapturingPolicy capturingPolicy() {
            return new CapturingPolicy(new LongTransactionPolicy(Duration.ZERO));
        }

        @Bean
        CapturingReporter capturingReporter() {
            return new CapturingReporter();
        }

        @Bean
        TransactionObservation transactionObservation(
                ActualTransactionDetector detector,
                TransactionGuardContextRegistry registry,
                CapturingPolicy policy,
                CapturingReporter reporter
        ) {
            return new TransactionObservation(detector, registry, List.of(policy), reporter);
        }

        @Bean
        TransactionGuardAspect transactionGuardAspect(TransactionObservation observation) {
            return new TransactionGuardAspect(observation);
        }

        @Bean
        TransactionGuardJdbcRecorder transactionGuardJdbcRecorder(TransactionGuardContextRegistry registry) {
            return new TransactionGuardJdbcRecorder(registry);
        }

        @Bean
        TransactionGuardJdbcAspect transactionGuardJdbcAspect(TransactionGuardJdbcRecorder recorder) {
            return new TransactionGuardJdbcAspect(recorder);
        }

        @Bean
        TransactionGuardRedisRecorder transactionGuardRedisRecorder(TransactionGuardContextRegistry registry) {
            return new TransactionGuardRedisRecorder(registry);
        }

        @Bean
        TransactionGuardRedisAspect transactionGuardRedisAspect(TransactionGuardRedisRecorder recorder) {
            return new TransactionGuardRedisAspect(recorder);
        }

        @Bean
        TransactionGuardKafkaRecorder transactionGuardKafkaRecorder(TransactionGuardContextRegistry registry) {
            return new TransactionGuardKafkaRecorder(registry);
        }

        @Bean
        TransactionGuardKafkaAspect transactionGuardKafkaAspect(TransactionGuardKafkaRecorder recorder) {
            return new TransactionGuardKafkaAspect(recorder);
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate() {
            ProducerFactory<String, String> producerFactory =
                    (ProducerFactory<String, String>) Proxy.newProxyInstance(
                            ProducerFactory.class.getClassLoader(),
                            new Class<?>[]{ProducerFactory.class},
                            (proxy, method, args) -> {
                                if (method.getReturnType() == boolean.class) return false;
                                if (Map.class.isAssignableFrom(method.getReturnType())) return Map.of();
                                return null;
                            });
            return new TestKafkaTemplate(producerFactory);
        }

        @Bean
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> redisValueOperations() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "TestValueOperations";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> null;
                            };
                        }
                        if (args != null && args.length > 0 && "fail".equals(args[0])) {
                            throw new IllegalStateException("redis failure with secret-value");
                        }
                        return method.getName().equals("get") ? "value" : null;
                    });
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        NestedService nestedService(
                TransactionGuardContextRegistry registry,
                ValueOperations<String, String> redisValueOperations,
                KafkaTemplate<String, String> kafkaTemplate
        ) {
            return new NestedService(registry, redisValueOperations, kafkaTemplate);
        }

        @Bean
        ScenarioService scenarioService(
                TransactionGuardContextRegistry registry,
                NestedService nestedService,
                JdbcTemplate jdbcTemplate,
                ValueOperations<String, String> redisValueOperations,
                KafkaTemplate<String, String> kafkaTemplate,
                DataSource dataSource
        ) {
            return new ScenarioService(
                    registry, nestedService, jdbcTemplate, redisValueOperations, kafkaTemplate, dataSource);
        }
    }

    static class ScenarioService {

        private final TransactionGuardContextRegistry registry;
        private final NestedService nestedService;
        private final JdbcTemplate jdbcTemplate;
        private final ValueOperations<String, String> redisValueOperations;
        private final KafkaTemplate<String, String> kafkaTemplate;
        private final DataSource dataSource;

        ScenarioService(
                TransactionGuardContextRegistry registry,
                NestedService nestedService,
                JdbcTemplate jdbcTemplate,
                ValueOperations<String, String> redisValueOperations,
                KafkaTemplate<String, String> kafkaTemplate,
                DataSource dataSource
        ) {
            this.registry = registry;
            this.nestedService = nestedService;
            this.jdbcTemplate = jdbcTemplate;
            this.redisValueOperations = redisValueOperations;
            this.kafkaTemplate = kafkaTemplate;
            this.dataSource = dataSource;
        }

        boolean withoutTransaction() {
            return registry.currentContext().isPresent();
        }

        @Transactional
        boolean contextPresentInTransaction() {
            return registry.currentContext().isPresent();
        }

        @Transactional
        void rollsBack() {
            throw new IllegalStateException("rollback");
        }

        @Transactional
        void executeQueries() {
            jdbcTemplate.queryForObject("select 1 /* top-secret */", Integer.class);
            jdbcTemplate.update("create table phase_one_probe(id integer)");
            jdbcTemplate.update("insert into phase_one_probe(id) values (?)", 1);
        }

        @Transactional
        void executeSingleQuery() {
            jdbcTemplate.queryForObject("select 1", Integer.class);
        }

        @Transactional
        void executeFailingQuery() {
            jdbcTemplate.queryForObject("select * from missing_secret_table", Integer.class);
        }

        @Transactional
        void executeThroughUnwrappedConnection() {
            try (Connection connection = dataSource.getConnection()) {
                Connection unwrapped = connection.unwrap(Connection.class);
                assertSame(connection, unwrapped);
                try (PreparedStatement statement = unwrapped.prepareStatement("select 1")) {
                    statement.executeQuery();
                }
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Transactional
        void executeBatchAndCallable() {
            try (Connection connection = dataSource.getConnection();
                 Statement batch = connection.createStatement()) {
                batch.addBatch("create table phase_one_batch(id integer)");
                batch.addBatch("insert into phase_one_batch(id) values (1)");
                batch.executeBatch();
                try (CallableStatement callable = connection.prepareCall("call 1")) {
                    callable.execute();
                }
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        }

        void executeIoWithoutTransaction() {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            redisValueOperations.get("outside-secret-key");
            kafkaTemplate.send("outside-secret-topic", "outside-secret-payload").join();
        }

        @Transactional
        void executeRedisOperations() {
            redisValueOperations.get("secret-key");
            redisValueOperations.set("secret-key", "secret-value");
        }

        @Transactional
        void executeFailingRedisOperation() {
            redisValueOperations.get("fail");
        }

        @Transactional
        void executeKafkaSend() {
            kafkaTemplate.send("secret-topic", "secret-payload").join();
        }

        @Transactional
        void executeFailingKafkaSend() {
            kafkaTemplate.send("fail", "secret-payload").join();
        }

        @Transactional
        CompletableFuture<SendResult<String, String>> executePendingKafkaSend() {
            return kafkaTemplate.send("pending", "secret-payload");
        }

        @Transactional
        void outerRequired() {
            nestedService.required();
        }

        @Transactional
        NestedIds outerRequiresNew() {
            String before = currentId();
            String inner = nestedService.requiresNew();
            return new NestedIds(before, inner, currentId());
        }

        @Transactional
        NotSupportedState outerNotSupported() {
            boolean before = registry.currentContext().isPresent();
            boolean inner = nestedService.notSupported();
            return new NotSupportedState(before, inner, registry.currentContext().isPresent());
        }

        @Transactional
        void outerIoRequiresNew() {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            nestedService.requiresNewIo();
        }

        @Transactional
        void outerWithNotSupportedIo() {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            nestedService.notSupportedIo();
            jdbcTemplate.queryForObject("select 1", Integer.class);
        }

        private String currentId() {
            return registry.currentContext().orElseThrow().transactionId();
        }
    }

    static class TestKafkaTemplate extends KafkaTemplate<String, String> {
        TestKafkaTemplate(ProducerFactory<String, String> producerFactory) {
            super(producerFactory);
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String data) {
            if ("fail".equals(topic)) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("secret kafka failure"));
            }
            if ("pending".equals(topic)) {
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    static class NestedService {

        private final TransactionGuardContextRegistry registry;
        private final ValueOperations<String, String> redisValueOperations;
        private final KafkaTemplate<String, String> kafkaTemplate;

        NestedService(
                TransactionGuardContextRegistry registry,
                ValueOperations<String, String> redisValueOperations,
                KafkaTemplate<String, String> kafkaTemplate
        ) {
            this.registry = registry;
            this.redisValueOperations = redisValueOperations;
            this.kafkaTemplate = kafkaTemplate;
        }

        @Transactional
        void required() {
            assertTrue(registry.currentContext().isPresent());
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        String requiresNew() {
            return registry.currentContext().orElseThrow().transactionId();
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        boolean notSupported() {
            return registry.currentContext().isPresent();
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        void requiresNewIo() {
            redisValueOperations.get("inner-secret-key");
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void notSupportedIo() {
            redisValueOperations.get("not-supported-secret-key");
            kafkaTemplate.send("not-supported-topic", "secret-payload").join();
        }
    }

    static final class CapturingPolicy implements TransactionGuardPolicy {

        private final TransactionGuardPolicy delegate;
        private final List<TransactionSnapshot> snapshots = new CopyOnWriteArrayList<>();

        CapturingPolicy(TransactionGuardPolicy delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
            snapshots.add(snapshot);
            return delegate.evaluate(snapshot);
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

        void clear() {
            violations.clear();
        }
    }
}
