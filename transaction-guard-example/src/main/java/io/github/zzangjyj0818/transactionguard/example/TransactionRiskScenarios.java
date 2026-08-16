package io.github.zzangjyj0818.transactionguard.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Transactional examples that intentionally trigger TG001, TG002, and TG003. */
@Service
public class TransactionRiskScenarios {

    private final RestClient restClient;
    private final String remoteBaseUrl;

    /**
     * Creates scenarios using Boot's customized RestClient builder.
     *
     * @param restClientBuilder Boot-managed builder
     * @param remoteBaseUrl downstream service base URL
     */
    public TransactionRiskScenarios(
            RestClient.Builder restClientBuilder,
            @Value("${example.remote-base-url:http://localhost:${server.port:8080}}") String remoteBaseUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.remoteBaseUrl = remoteBaseUrl;
    }

    /**
     * Holds a database transaction open long enough to produce TG001.
     *
     * @return completion message
     */
    @Transactional
    public String longTransaction() {
        pause(Duration.ofMillis(200));
        return "TG001 scenario completed";
    }

    /**
     * Performs a fast HTTP call inside a transaction to produce TG002.
     *
     * @return downstream response
     */
    @Transactional
    public String externalCallInTransaction() {
        return restClient.get().uri(remoteBaseUrl + "/remote/fast").retrieve().body(String.class);
    }

    /**
     * Performs a slow HTTP call inside a transaction to produce TG002 and TG003.
     *
     * @return downstream response
     */
    @Transactional
    public String slowExternalCallInTransaction() {
        return restClient.get().uri(remoteBaseUrl + "/remote/slow").retrieve().body(String.class);
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Example scenario was interrupted", exception);
        }
    }
}
