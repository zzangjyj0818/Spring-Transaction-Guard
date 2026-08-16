package io.github.zzangjyj0818.transactionguard.starter;

import io.github.zzangjyj0818.transactionguard.autoconfigure.TransactionGuardProperties;
import io.github.zzangjyj0818.transactionguard.core.exception.TransactionGuardViolationException;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.framework.Advised;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionGuardStarterIntegrationTest {

    @Test
    void starterDiscoversAutoConfigurationWithoutEnableAnnotation() {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.main.web-application-type", "none",
                "spring.main.banner-mode", "off",
                "logging.level.root", "off"
        ));

        try (ConfigurableApplicationContext context = application.run()) {
            assertEquals(1, context.getBeansOfType(TransactionObservation.class).size());
            assertTrue(context.getBean(TransactionGuardProperties.class).isEnabled());
            assertTrue(AopUtils.isAopProxy(context.getBean(TransactionalService.class)));
            TransactionalService service = context.getBean(TransactionalService.class);
            assertTrue(service.invoke(), () -> advisorDescription(service));
        }
    }

    @Test
    void throwModeRaisesViolationBeforeCommitAndRollsBackTransaction() {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.main.web-application-type", "none",
                "spring.main.banner-mode", "off",
                "logging.level.root", "off",
                "transaction-guard.transaction.max-duration", "0ns",
                "transaction-guard.violation.mode", "throw"
        ));

        try (ConfigurableApplicationContext context = application.run()) {
            TransactionGuardViolationException exception = assertThrows(
                    TransactionGuardViolationException.class,
                    () -> context.getBean(TransactionalService.class).invoke()
            );

            assertEquals("TG001", exception.violations().getFirst().code());
        }
    }

    @Test
    void logModePreservesBusinessFlowWhenCustomReporterFails() {
        SpringApplication application = new SpringApplication(
                TestApplication.class, FailingReporterConfiguration.class);
        application.setDefaultProperties(Map.of(
                "spring.main.web-application-type", "none",
                "spring.main.banner-mode", "off",
                "logging.level.root", "off",
                "transaction-guard.transaction.max-duration", "0ns",
                "transaction-guard.violation.mode", "log"
        ));

        try (ConfigurableApplicationContext context = application.run()) {
            assertTrue(context.getBean(TransactionalService.class).invoke());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        TransactionalService transactionalService(TransactionGuardContextRegistry registry) {
            return new TransactionalService(registry);
        }

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
    }

    static class TransactionalService {
        private final TransactionGuardContextRegistry registry;

        TransactionalService(TransactionGuardContextRegistry registry) {
            this.registry = registry;
        }

        @Transactional
        public boolean invoke() {
            return registry.currentContext().isPresent();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingReporterConfiguration {
        @Bean
        TransactionGuardReporter transactionGuardReporter() {
            return violations -> {
                throw new IllegalStateException("reporter failure");
            };
        }
    }

    private static String advisorDescription(Object bean) {
        return Arrays.stream(((Advised) bean).getAdvisors())
                .map(advisor -> advisor.getClass().getName() + " order="
                        + (advisor instanceof Ordered ordered ? ordered.getOrder() : "unordered"))
                .toList()
                .toString();
    }
}
