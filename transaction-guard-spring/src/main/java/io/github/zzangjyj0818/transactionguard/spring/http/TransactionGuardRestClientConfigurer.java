package io.github.zzangjyj0818.transactionguard.spring.http;

import org.springframework.web.client.RestClient;

import java.util.Objects;

/** Applies Transaction Guard instrumentation to a Spring RestClient builder. */
public final class TransactionGuardRestClientConfigurer {

    private final TransactionGuardHttpInterceptor interceptor;

    /**
     * Creates a builder configurer for the supplied interceptor.
     *
     * @param interceptor Transaction Guard HTTP interceptor
     */
    public TransactionGuardRestClientConfigurer(TransactionGuardHttpInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor must not be null");
    }

    /**
     * Adds the interceptor without replacing application-defined interceptors.
     *
     * @param builder builder to customize
     */
    public void configure(RestClient.Builder builder) {
        Objects.requireNonNull(builder, "builder must not be null").requestInterceptor(interceptor);
    }
}
