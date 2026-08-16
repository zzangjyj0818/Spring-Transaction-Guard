package io.github.zzangjyj0818.transactionguard.autoconfigure;

import io.github.zzangjyj0818.transactionguard.autoconfigure.endpoint.TransactionGuardEndpoint;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Optional read-only Actuator endpoint for Transaction Guard. */
@AutoConfiguration(after = {TransactionGuardMetricsAutoConfiguration.class, TransactionGuardAutoConfiguration.class})
@ConditionalOnClass(Endpoint.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnAvailableEndpoint(endpoint = TransactionGuardEndpoint.class)
public class TransactionGuardEndpointAutoConfiguration {

    /** Creates the endpoint when it is available under Spring Boot's endpoint policy. */
    @Bean
    @ConditionalOnMissingBean
    TransactionGuardEndpoint transactionGuardEndpoint(
            MeterRegistry registry,
            TransactionGuardProperties properties
    ) {
        return new TransactionGuardEndpoint(registry, properties);
    }
}
