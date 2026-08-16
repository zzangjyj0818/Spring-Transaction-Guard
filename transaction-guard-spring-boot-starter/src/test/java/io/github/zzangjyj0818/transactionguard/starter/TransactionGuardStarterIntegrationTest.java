package io.github.zzangjyj0818.transactionguard.starter;

import io.github.zzangjyj0818.transactionguard.autoconfigure.TransactionGuardProperties;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        TransactionalService transactionalService() {
            return new TransactionalService();
        }
    }

    static class TransactionalService {
        @Transactional
        void invoke() {
        }
    }
}
