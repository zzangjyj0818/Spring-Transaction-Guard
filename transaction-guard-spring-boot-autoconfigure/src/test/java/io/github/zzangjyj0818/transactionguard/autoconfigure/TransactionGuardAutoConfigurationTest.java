package io.github.zzangjyj0818.transactionguard.autoconfigure;

import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.reporter.LoggingTransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.core.reporter.ThrowingTransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.spring.aop.TransactionGuardAspect;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardHttpInterceptor;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservation;
import io.github.zzangjyj0818.transactionguard.autoconfigure.metrics.TransactionGuardMetrics;
import io.github.zzangjyj0818.transactionguard.autoconfigure.endpoint.TransactionGuardEndpoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TransactionGuardAutoConfiguration.class));

    @Test
    void createsDefaultGuardBeansAndProperties() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            assertEquals(1, context.getBeansOfType(TransactionObservation.class).size());
            assertEquals(1, context.getBeansOfType(TransactionGuardAspect.class).size());
            assertEquals(5, context.getBeansOfType(TransactionGuardPolicy.class).size());
            assertInstanceOf(LoggingTransactionGuardReporter.class,
                    context.getBean(TransactionGuardReporter.class));

            TransactionGuardProperties properties = context.getBean(TransactionGuardProperties.class);
            assertTrue(properties.isEnabled());
            assertEquals(Duration.ofSeconds(2), properties.getTransaction().getMaxDuration());
            assertTrue(properties.getExternalCall().isEnabled());
            assertEquals(Duration.ofSeconds(1), properties.getExternalCall().getSlowThreshold());
            assertTrue(properties.getRedis().isEnabled());
            assertEquals(Duration.ofSeconds(1), properties.getRedis().getSlowThreshold());
            assertEquals(TransactionGuardProperties.Mode.LOG, properties.getViolation().getMode());
            assertEquals(List.of(), properties.getExternalCall().getIgnoreHosts());
            assertEquals(List.of(), properties.getExternalCall().getIgnoreEndpoints());
            assertEquals(List.of(), properties.getExternalCall().getAllowHosts());
            assertEquals(List.of(), properties.getExternalCall().getAllowEndpoints());
            assertEquals(Set.of(), properties.getViolation().getDisabledCodes());
        });
    }

    @Test
    void disablesEveryGuardBeanWhenGuardIsDisabled() {
        contextRunner.withPropertyValues("transaction-guard.enabled=false").run(context -> {
            assertTrue(context.getBeansOfType(TransactionObservation.class).isEmpty());
            assertTrue(context.getBeansOfType(TransactionGuardAspect.class).isEmpty());
            assertTrue(context.getBeansOfType(TransactionGuardReporter.class).isEmpty());
            assertTrue(context.getBeansOfType(TransactionGuardProperties.class).isEmpty());
        });
    }

    @Test
    void bindsThresholdsAndCreatesThrowingReporter() {
        contextRunner.withPropertyValues(
                "transaction-guard.transaction.max-duration=750ms",
                "transaction-guard.external-call.slow-threshold=250ms",
                "transaction-guard.violation.mode=throw"
        ).run(context -> {
            TransactionGuardProperties properties = context.getBean(TransactionGuardProperties.class);
            assertEquals(Duration.ofMillis(750), properties.getTransaction().getMaxDuration());
            assertEquals(Duration.ofMillis(250), properties.getExternalCall().getSlowThreshold());
            assertInstanceOf(ThrowingTransactionGuardReporter.class,
                    context.getBean(TransactionGuardReporter.class));
        });
    }

    @Test
    void rejectsNegativeDurationConfiguration() {
        contextRunner.withPropertyValues("transaction-guard.transaction.max-duration=-1ms")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void bindsPolicyControlProperties() {
        contextRunner.withPropertyValues(
                "transaction-guard.external-call.ignore-hosts[0]=metadata.internal",
                "transaction-guard.external-call.ignore-endpoints[0]=health.internal/ready",
                "transaction-guard.external-call.allow-hosts[0]=payments.internal",
                "transaction-guard.external-call.allow-endpoints[0]=audit.internal/events/*",
                "transaction-guard.violation.disabled-codes[0]=TG001",
                "transaction-guard.violation.disabled-codes[1]=TG003"
        ).run(context -> {
            TransactionGuardProperties properties = context.getBean(TransactionGuardProperties.class);
            assertEquals(List.of("metadata.internal"), properties.getExternalCall().getIgnoreHosts());
            assertEquals(List.of("health.internal/ready"), properties.getExternalCall().getIgnoreEndpoints());
            assertEquals(List.of("payments.internal"), properties.getExternalCall().getAllowHosts());
            assertEquals(List.of("audit.internal/events/*"), properties.getExternalCall().getAllowEndpoints());
            assertEquals(Set.of(
                    TransactionGuardProperties.ViolationCode.TG001,
                    TransactionGuardProperties.ViolationCode.TG003
            ), properties.getViolation().getDisabledCodes());
        });
    }

    @Test
    void rejectsUnknownDisabledViolationCode() {
        contextRunner.withPropertyValues("transaction-guard.violation.disabled-codes[0]=TG999")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void disabledCodesAndAllowRulesSuppressOnlyConfiguredViolations() {
        contextRunner.withPropertyValues(
                "transaction-guard.transaction.max-duration=1ns",
                "transaction-guard.external-call.slow-threshold=1ns",
                "transaction-guard.external-call.allow-hosts[0]=payments.example.com",
                "transaction-guard.violation.disabled-codes[0]=TG001"
        ).run(context -> {
            TransactionSnapshot snapshot = new TransactionSnapshot(
                    "tx-1",
                    new TransactionEntryPoint("Example", "call", "Example#call"),
                    Duration.ofSeconds(1),
                    TransactionOutcome.COMMITTED,
                    List.of(new ExternalCallObservation(
                            ExternalClientType.REST_CLIENT,
                            "GET",
                            "payments.example.com",
                            "/payments",
                            Duration.ofSeconds(1).toNanos(),
                            ExternalCallOutcome.SUCCESS,
                            null)));

            List<String> codes = context.getBeansOfType(TransactionGuardPolicy.class).values().stream()
                    .flatMap(policy -> policy.evaluate(snapshot).stream())
                    .map(violation -> violation.code())
                    .toList();

            assertEquals(List.of(), codes);
            assertEquals(1, snapshot.externalCalls().size());
        });
    }

    @Test
    void backsOffForApplicationReporter() {
        contextRunner.withUserConfiguration(CustomReporterConfiguration.class).run(context -> {
            assertEquals(1, context.getBeansOfType(TransactionGuardReporter.class).size());
            assertInstanceOf(CapturingReporter.class, context.getBean(TransactionGuardReporter.class));
        });
    }

    @Test
    void disablesHttpPoliciesAndInstrumentationOnly() {
        contextRunner.withPropertyValues("transaction-guard.external-call.enabled=false").run(context -> {
            assertEquals(3, context.getBeansOfType(TransactionGuardPolicy.class).size());
            assertTrue(context.getBeansOfType(TransactionGuardHttpInterceptor.class).isEmpty());
            assertTrue(context.getBeansOfType(TransactionGuardRestClientCustomizer.class).isEmpty());
            assertEquals(1, context.getBeansOfType(TransactionObservation.class).size());
        });
    }

    @Test
    void contributesCustomizerToBootManagedRestClientBuilders() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RestClientAutoConfiguration.class,
                        TransactionGuardAutoConfiguration.class
                ))
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(TransactionGuardRestClientCustomizer.class).size());
                    assertFalse(context.getBeansOfType(org.springframework.web.client.RestClient.Builder.class)
                            .isEmpty());
                });
    }

    @Test
    void createsMetricsListenerOnlyWhenMeterRegistryBeanExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TransactionGuardMetricsAutoConfiguration.class,
                        TransactionGuardAutoConfiguration.class))
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertEquals(1, context.getBeansOfType(TransactionGuardMetrics.class).size()));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TransactionGuardMetricsAutoConfiguration.class,
                        TransactionGuardAutoConfiguration.class))
                .run(context -> assertTrue(context.getBeansOfType(TransactionGuardMetrics.class).isEmpty()));
    }

    @Test
    void doesNotCreateMetricsListenerWhenGuardIsDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TransactionGuardMetricsAutoConfiguration.class,
                        TransactionGuardAutoConfiguration.class))
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("transaction-guard.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(TransactionGuardMetrics.class).isEmpty()));
    }

    @Test
    void createsActuatorEndpointOnlyWithMetricsRegistryAndAvailableEndpoint() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TransactionGuardMetricsAutoConfiguration.class,
                        TransactionGuardAutoConfiguration.class,
                        TransactionGuardEndpointAutoConfiguration.class))
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues(
                        "management.endpoint.transactionguard.access=READ_ONLY",
                        "management.endpoints.web.exposure.include=transactionguard")
                .run(context -> assertEquals(1,
                        context.getBeansOfType(TransactionGuardEndpoint.class).size()));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TransactionGuardMetricsAutoConfiguration.class,
                        TransactionGuardAutoConfiguration.class,
                        TransactionGuardEndpointAutoConfiguration.class))
                .run(context -> assertTrue(
                        context.getBeansOfType(TransactionGuardEndpoint.class).isEmpty()));
    }

    @Test
    void respectsActuatorEndpointAccessPolicy() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TransactionGuardMetricsAutoConfiguration.class,
                        TransactionGuardAutoConfiguration.class,
                        TransactionGuardEndpointAutoConfiguration.class))
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("management.endpoint.transactionguard.access=NONE")
                .run(context -> assertTrue(
                        context.getBeansOfType(TransactionGuardEndpoint.class).isEmpty()));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomReporterConfiguration {
        @Bean
        TransactionGuardReporter customReporter() {
            return new CapturingReporter();
        }
    }

    static final class CapturingReporter implements TransactionGuardReporter {
        @Override
        public void report(List<io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation> violations) {
        }
    }
}
