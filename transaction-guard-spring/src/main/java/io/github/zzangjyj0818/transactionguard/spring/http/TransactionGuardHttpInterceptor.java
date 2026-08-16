package io.github.zzangjyj0818.transactionguard.spring.http;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Objects;

/** Observes synchronous Spring RestClient calls made inside a Guard-observed transaction. */
public final class TransactionGuardHttpInterceptor implements ClientHttpRequestInterceptor {

    private final TransactionGuardHttpRecorder recorder;

    /**
     * Creates a RestClient interceptor.
     *
     * @param recorder external call recorder
     */
    public TransactionGuardHttpInterceptor(TransactionGuardHttpRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        if (!recorder.isTransactionObserved()) {
            return execution.execute(request, body);
        }

        long startedAtNanos = recorder.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long durationNanos = elapsedSince(startedAtNanos);
            if (response.getStatusCode().isError()) {
                recorder.recordHttpFailure(request, durationNanos);
            } else {
                recorder.recordSuccess(request, durationNanos);
            }
            return response;
        } catch (IOException | RuntimeException failure) {
            recorder.recordFailure(request, elapsedSince(startedAtNanos), failure);
            throw failure;
        }
    }

    private long elapsedSince(long startedAtNanos) {
        long completedAtNanos = recorder.nanoTime();
        return completedAtNanos >= startedAtNanos ? completedAtNanos - startedAtNanos : 0;
    }
}
