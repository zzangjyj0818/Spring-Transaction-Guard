package io.github.zzangjyj0818.transactionguard.autoconfigure;

import io.github.zzangjyj0818.transactionguard.core.policy.ExternalHttpCallPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.ExternalCallRuleMatcher;
import io.github.zzangjyj0818.transactionguard.core.policy.LongTransactionPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.SlowExternalHttpCallPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.RedisOperationPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.SlowRedisOperationPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.KafkaProducerCallPolicy;
import io.github.zzangjyj0818.transactionguard.core.policy.SlowKafkaProducerCallPolicy;
import io.github.zzangjyj0818.transactionguard.core.reporter.LoggingTransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.core.reporter.ThrowingTransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.spring.aop.TransactionGuardAspect;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardHttpInterceptor;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardHttpRecorder;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardRestClientConfigurer;
import io.github.zzangjyj0818.transactionguard.spring.transaction.ActualTransactionDetector;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservation;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservationListener;
import io.github.zzangjyj0818.transactionguard.spring.redis.TransactionGuardRedisAspect;
import io.github.zzangjyj0818.transactionguard.spring.redis.TransactionGuardRedisRecorder;
import io.github.zzangjyj0818.transactionguard.spring.kafka.TransactionGuardKafkaAspect;
import io.github.zzangjyj0818.transactionguard.spring.kafka.TransactionGuardKafkaRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

/** Spring Boot auto-configuration for imperative transaction observation. */
@AutoConfiguration
@EnableConfigurationProperties(TransactionGuardProperties.class)
@ConditionalOnClass(TransactionObservation.class)
@ConditionalOnProperty(prefix = "transaction-guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionGuardAutoConfiguration {

    /** Creates the Transaction Guard auto-configuration. */
    public TransactionGuardAutoConfiguration() {
    }

    /** Creates the actual Spring transaction detector. */
    @Bean
    @ConditionalOnMissingBean
    ActualTransactionDetector transactionGuardActualTransactionDetector() {
        return new ActualTransactionDetector();
    }

    /** Creates the Spring transaction resource registry. */
    @Bean
    @ConditionalOnMissingBean
    TransactionGuardContextRegistry transactionGuardContextRegistry() {
        return new TransactionGuardContextRegistry();
    }

    /** Creates the TG001 policy using the configured duration threshold. */
    @Bean("transactionGuardLongTransactionPolicy")
    @ConditionalOnMissingBean(name = "transactionGuardLongTransactionPolicy")
    TransactionGuardPolicy transactionGuardLongTransactionPolicy(TransactionGuardProperties properties) {
        return configuredPolicy(
                properties,
                TransactionGuardProperties.ViolationCode.TG001,
                () -> new LongTransactionPolicy(properties.getTransaction().getMaxDuration()));
    }

    /** Creates the TG002 policy when external call observation is enabled. */
    @Bean("transactionGuardExternalHttpCallPolicy")
    @ConditionalOnMissingBean(name = "transactionGuardExternalHttpCallPolicy")
    @ConditionalOnProperty(
            prefix = "transaction-guard.external-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    TransactionGuardPolicy transactionGuardExternalHttpCallPolicy(
            TransactionGuardProperties properties,
            ExternalCallRuleMatcher ruleMatcher
    ) {
        return configuredPolicy(
                properties,
                TransactionGuardProperties.ViolationCode.TG002,
                () -> new ExternalHttpCallPolicy(call -> !ruleMatcher.isAllowed(call)));
    }

    /** Creates the TG003 policy using the configured external call threshold. */
    @Bean("transactionGuardSlowExternalHttpCallPolicy")
    @ConditionalOnMissingBean(name = "transactionGuardSlowExternalHttpCallPolicy")
    @ConditionalOnProperty(
            prefix = "transaction-guard.external-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    TransactionGuardPolicy transactionGuardSlowExternalHttpCallPolicy(
            TransactionGuardProperties properties,
            ExternalCallRuleMatcher ruleMatcher
    ) {
        return configuredPolicy(
                properties,
                TransactionGuardProperties.ViolationCode.TG003,
                () -> new SlowExternalHttpCallPolicy(
                        properties.getExternalCall().getSlowThreshold(),
                        call -> !ruleMatcher.isAllowed(call)));
    }

    /** Creates TG004 when imperative Redis observation is enabled. */
    @Bean("transactionGuardRedisOperationPolicy")
    @ConditionalOnMissingBean(name = "transactionGuardRedisOperationPolicy")
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisOperations")
    @ConditionalOnProperty(prefix = "transaction-guard.redis", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardPolicy transactionGuardRedisOperationPolicy(TransactionGuardProperties properties) {
        return configuredPolicy(properties, TransactionGuardProperties.ViolationCode.TG004,
                RedisOperationPolicy::new);
    }

    /** Creates TG005 using the configured Redis duration threshold. */
    @Bean("transactionGuardSlowRedisOperationPolicy")
    @ConditionalOnMissingBean(name = "transactionGuardSlowRedisOperationPolicy")
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisOperations")
    @ConditionalOnProperty(prefix = "transaction-guard.redis", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardPolicy transactionGuardSlowRedisOperationPolicy(TransactionGuardProperties properties) {
        return configuredPolicy(properties, TransactionGuardProperties.ViolationCode.TG005,
                () -> new SlowRedisOperationPolicy(properties.getRedis().getSlowThreshold()));
    }

    /** Creates the privacy-safe Redis operation recorder. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisOperations")
    @ConditionalOnProperty(prefix = "transaction-guard.redis", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardRedisRecorder transactionGuardRedisRecorder(TransactionGuardContextRegistry registry) {
        return new TransactionGuardRedisRecorder(registry);
    }

    /** Instruments Spring Data Redis operation interfaces. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisOperations")
    @ConditionalOnProperty(prefix = "transaction-guard.redis", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardRedisAspect transactionGuardRedisAspect(TransactionGuardRedisRecorder recorder) {
        return new TransactionGuardRedisAspect(recorder);
    }

    /** Creates TG006 for Kafka producer calls. */
    @Bean("transactionGuardKafkaProducerCallPolicy")
    @ConditionalOnMissingBean(name = "transactionGuardKafkaProducerCallPolicy")
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(prefix = "transaction-guard.kafka", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardPolicy transactionGuardKafkaProducerCallPolicy(TransactionGuardProperties properties) {
        return configuredPolicy(properties, TransactionGuardProperties.ViolationCode.TG006,
                KafkaProducerCallPolicy::new);
    }

    /** Creates TG007 for slow Kafka producer calls. */
    @Bean("transactionGuardSlowKafkaProducerCallPolicy")
    @ConditionalOnMissingBean(name = "transactionGuardSlowKafkaProducerCallPolicy")
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(prefix = "transaction-guard.kafka", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardPolicy transactionGuardSlowKafkaProducerCallPolicy(TransactionGuardProperties properties) {
        return configuredPolicy(properties, TransactionGuardProperties.ViolationCode.TG007,
                () -> new SlowKafkaProducerCallPolicy(properties.getKafka().getSlowThreshold()));
    }

    /** Creates privacy-safe Kafka producer instrumentation. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(prefix = "transaction-guard.kafka", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardKafkaRecorder transactionGuardKafkaRecorder(TransactionGuardContextRegistry registry) {
        return new TransactionGuardKafkaRecorder(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(prefix = "transaction-guard.kafka", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    TransactionGuardKafkaAspect transactionGuardKafkaAspect(TransactionGuardKafkaRecorder recorder) {
        return new TransactionGuardKafkaAspect(recorder);
    }

    /** Creates the configured default Reporter unless the application supplies one. */
    @Bean
    @ConditionalOnMissingBean(TransactionGuardReporter.class)
    TransactionGuardReporter transactionGuardReporter(TransactionGuardProperties properties) {
        return properties.getViolation().getMode() == TransactionGuardProperties.Mode.THROW
                ? new ThrowingTransactionGuardReporter()
                : new LoggingTransactionGuardReporter();
    }

    /** Creates transaction lifecycle observation with every available Guard policy. */
    @Bean
    @ConditionalOnMissingBean
    TransactionObservation transactionGuardObservation(
            ActualTransactionDetector detector,
            TransactionGuardContextRegistry registry,
            ObjectProvider<TransactionGuardPolicy> policies,
            TransactionGuardReporter reporter,
            ObjectProvider<TransactionObservationListener> listeners,
            TransactionGuardProperties properties
    ) {
        List<TransactionGuardPolicy> policyList = policies.orderedStream().toList();
        List<TransactionObservationListener> listenerList = listeners.orderedStream().toList();
        boolean propagateGuardFailures = properties.getViolation().getMode() == TransactionGuardProperties.Mode.THROW;
        return new TransactionObservation(
                detector, registry, policyList, reporter, listenerList, propagateGuardFailures);
    }

    /** Creates the transactional entry point aspect. */
    @Bean
    @ConditionalOnMissingBean
    TransactionGuardAspect transactionGuardAspect(TransactionObservation observation) {
        return new TransactionGuardAspect(observation);
    }

    /** Creates the HTTP observation recorder when RestClient instrumentation is enabled. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RestClient.class)
    @ConditionalOnProperty(
            prefix = "transaction-guard.external-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    TransactionGuardHttpRecorder transactionGuardHttpRecorder(
            TransactionGuardContextRegistry registry,
            ExternalCallRuleMatcher ruleMatcher
    ) {
        return new TransactionGuardHttpRecorder(registry, ruleMatcher);
    }

    /** Creates the RestClient interceptor when external call observation is enabled. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RestClient.class)
    @ConditionalOnProperty(
            prefix = "transaction-guard.external-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    TransactionGuardHttpInterceptor transactionGuardHttpInterceptor(TransactionGuardHttpRecorder recorder) {
        return new TransactionGuardHttpInterceptor(recorder);
    }

    /** Creates the framework-level RestClient builder configurer. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RestClient.class)
    @ConditionalOnProperty(
            prefix = "transaction-guard.external-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    TransactionGuardRestClientConfigurer transactionGuardRestClientConfigurer(
            TransactionGuardHttpInterceptor interceptor
    ) {
        return new TransactionGuardRestClientConfigurer(interceptor);
    }

    /** Adds Transaction Guard instrumentation to Boot-managed RestClient builders. */
    @Bean
    @ConditionalOnMissingBean(TransactionGuardRestClientCustomizer.class)
    @ConditionalOnClass(name = "org.springframework.boot.restclient.RestClientCustomizer")
    @ConditionalOnProperty(
            prefix = "transaction-guard.external-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    TransactionGuardRestClientCustomizer transactionGuardRestClientCustomizer(
            TransactionGuardRestClientConfigurer configurer
    ) {
        return new TransactionGuardRestClientCustomizer(configurer);
    }

    /** Compiles client-neutral ignore and allow rules once during startup. */
    @Bean
    @ConditionalOnMissingBean
    ExternalCallRuleMatcher transactionGuardExternalCallRuleMatcher(TransactionGuardProperties properties) {
        TransactionGuardProperties.ExternalCall externalCall = properties.getExternalCall();
        return new ExternalCallRuleMatcher(
                externalCall.getIgnoreHosts(),
                externalCall.getIgnoreEndpoints(),
                externalCall.getAllowHosts(),
                externalCall.getAllowEndpoints());
    }

    private static TransactionGuardPolicy configuredPolicy(
            TransactionGuardProperties properties,
            TransactionGuardProperties.ViolationCode code,
            Supplier<TransactionGuardPolicy> policyFactory
    ) {
        if (properties.getViolation().getDisabledCodes().contains(code)) {
            return snapshot -> List.of();
        }
        return policyFactory.get();
    }
}
