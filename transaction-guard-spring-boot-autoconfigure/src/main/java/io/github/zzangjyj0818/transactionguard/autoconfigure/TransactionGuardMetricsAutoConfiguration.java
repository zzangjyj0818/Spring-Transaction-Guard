package io.github.zzangjyj0818.transactionguard.autoconfigure;

import io.github.zzangjyj0818.transactionguard.autoconfigure.metrics.TransactionGuardMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Optional Micrometer integration for Transaction Guard. */
@AutoConfiguration(before = TransactionGuardAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "transaction-guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionGuardMetricsAutoConfiguration {

    /** Creates the low-cardinality metrics completion listener. */
    @Bean
    @ConditionalOnMissingBean
    TransactionGuardMetrics transactionGuardMetrics(MeterRegistry registry) {
        return new TransactionGuardMetrics(registry);
    }
}
