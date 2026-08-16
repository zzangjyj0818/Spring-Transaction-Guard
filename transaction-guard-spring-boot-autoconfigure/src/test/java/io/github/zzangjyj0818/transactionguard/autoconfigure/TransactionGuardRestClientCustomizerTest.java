package io.github.zzangjyj0818.transactionguard.autoconfigure;

import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardHttpInterceptor;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardHttpRecorder;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardRestClientConfigurer;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionGuardRestClientCustomizerTest {

    @Test
    void customizesBuilderWithoutReplacingExistingInterceptors() {
        AtomicInteger existingCalls = new AtomicInteger();
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new SuccessfulRequestFactory())
                .requestInterceptor((request, body, execution) -> {
                    existingCalls.incrementAndGet();
                    return execution.execute(request, body);
                });
        TransactionGuardHttpInterceptor interceptor = new TransactionGuardHttpInterceptor(
                new TransactionGuardHttpRecorder(new TransactionGuardContextRegistry()));

        new TransactionGuardRestClientCustomizer(
                new TransactionGuardRestClientConfigurer(interceptor)).customize(builder);
        builder.build().get().uri("http://example.test/path").retrieve().toBodilessEntity();

        assertEquals(1, existingCalls.get());
    }

    private static final class SuccessfulRequestFactory implements ClientHttpRequestFactory {
        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            return new org.springframework.mock.http.client.MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected ClientHttpResponse executeInternal() {
                    return new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0], 200);
                }
            };
        }
    }
}
