package io.github.zzangjyj0818.transactionguard.autoconfigure;

import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardRestClientConfigurer;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/** Applies Transaction Guard instrumentation to Boot-managed RestClient builders. */
public final class TransactionGuardRestClientCustomizer implements RestClientCustomizer {

    private final TransactionGuardRestClientConfigurer configurer;

    /**
     * Creates a Boot RestClient customizer.
     *
     * @param configurer framework-level builder configurer
     */
    public TransactionGuardRestClientCustomizer(TransactionGuardRestClientConfigurer configurer) {
        this.configurer = Objects.requireNonNull(configurer, "configurer must not be null");
    }

    @Override
    public void customize(RestClient.Builder restClientBuilder) {
        configurer.configure(restClientBuilder);
    }
}
