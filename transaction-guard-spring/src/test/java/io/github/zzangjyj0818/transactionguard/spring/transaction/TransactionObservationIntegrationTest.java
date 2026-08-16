package io.github.zzangjyj0818.transactionguard.spring.transaction;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.policy.LongTransactionPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.spring.aop.TransactionGuardAspect;
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
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void evaluatesLongTransactionPolicyAndReportsTg001() {
        scenarioService.contextPresentInTransaction();

        assertEquals(1, reporter.violations().size());
        assertEquals("TG001", reporter.violations().getFirst().code());
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
                    scenarioService.contextPresentInTransaction();
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
        NestedService nestedService(TransactionGuardContextRegistry registry) {
            return new NestedService(registry);
        }

        @Bean
        ScenarioService scenarioService(TransactionGuardContextRegistry registry, NestedService nestedService) {
            return new ScenarioService(registry, nestedService);
        }
    }

    static class ScenarioService {

        private final TransactionGuardContextRegistry registry;
        private final NestedService nestedService;

        ScenarioService(TransactionGuardContextRegistry registry, NestedService nestedService) {
            this.registry = registry;
            this.nestedService = nestedService;
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

        private String currentId() {
            return registry.currentContext().orElseThrow().transactionId();
        }
    }

    static class NestedService {

        private final TransactionGuardContextRegistry registry;

        NestedService(TransactionGuardContextRegistry registry) {
            this.registry = registry;
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
