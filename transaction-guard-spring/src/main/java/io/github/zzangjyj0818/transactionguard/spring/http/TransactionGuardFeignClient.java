package io.github.zzangjyj0818.transactionguard.spring.http;

import feign.Client;
import feign.Request;
import feign.Response;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/** Decorates a blocking OpenFeign client with transaction-aware observation. */
public final class TransactionGuardFeignClient implements Client {

    private final Client delegate;
    private final TransactionGuardHttpRecorder recorder;

    /** Creates an observing Feign client decorator. */
    public TransactionGuardFeignClient(Client delegate, TransactionGuardHttpRecorder recorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        Objects.requireNonNull(request, "request must not be null");
        if (!recorder.isTransactionObserved()) {
            return delegate.execute(request, options);
        }

        String method = request.httpMethod().name();
        URI uri = URI.create(request.url());
        long startedAtNanos = recorder.nanoTime();
        try {
            Response response = delegate.execute(request, options);
            long durationNanos = elapsedSince(startedAtNanos);
            if (response.status() >= 400) {
                recorder.recordHttpFailure(ExternalClientType.OPEN_FEIGN, method, uri, durationNanos);
            } else {
                recorder.recordSuccess(ExternalClientType.OPEN_FEIGN, method, uri, durationNanos);
            }
            return response;
        } catch (IOException | RuntimeException failure) {
            recorder.recordFailure(
                    ExternalClientType.OPEN_FEIGN, method, uri, elapsedSince(startedAtNanos), failure);
            throw failure;
        }
    }

    private long elapsedSince(long startedAtNanos) {
        long completedAtNanos = recorder.nanoTime();
        return completedAtNanos >= startedAtNanos ? completedAtNanos - startedAtNanos : 0;
    }
}
