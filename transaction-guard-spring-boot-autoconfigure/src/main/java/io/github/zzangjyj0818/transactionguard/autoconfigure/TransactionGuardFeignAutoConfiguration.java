package io.github.zzangjyj0818.transactionguard.autoconfigure;

import feign.Capability;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardFeignCapability;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardHttpRecorder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Optional OpenFeign integration loaded only when Feign is on the application classpath. */
@AutoConfiguration(after = TransactionGuardAutoConfiguration.class)
@ConditionalOnClass(Capability.class)
@ConditionalOnProperty(prefix = "transaction-guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionGuardFeignAutoConfiguration {

    /** Creates the optional OpenFeign capability. */
    public TransactionGuardFeignAutoConfiguration() {
    }

    /** Adds transaction-aware observation to each OpenFeign client. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "transaction-guard.external-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    TransactionGuardFeignCapability transactionGuardFeignCapability(TransactionGuardHttpRecorder recorder) {
        return new TransactionGuardFeignCapability(recorder);
    }
}
