package io.github.zzangjyj0818.transactionguard.spring.http;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.policy.ExternalCallRuleMatcher;
import io.github.zzangjyj0818.transactionguard.spring.transaction.MonotonicClock;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry;
import org.springframework.http.HttpRequest;

import java.util.Objects;

/** Records sanitized RestClient calls into the currently observed transaction context. */
public final class TransactionGuardHttpRecorder {

    private final TransactionGuardContextRegistry contextRegistry;
    private final MonotonicClock clock;
    private final ExternalCallUriSanitizer uriSanitizer;
    private final ExternalCallRuleMatcher ruleMatcher;

    /**
     * Creates a recorder using the system monotonic clock.
     *
     * @param contextRegistry transaction context registry
     */
    public TransactionGuardHttpRecorder(TransactionGuardContextRegistry contextRegistry) {
        this(contextRegistry, ExternalCallRuleMatcher.none());
    }

    /** Creates a recorder with configured destination rules. */
    public TransactionGuardHttpRecorder(
            TransactionGuardContextRegistry contextRegistry,
            ExternalCallRuleMatcher ruleMatcher
    ) {
        this(contextRegistry, MonotonicClock.system(), new ExternalCallUriSanitizer(), ruleMatcher);
    }

    TransactionGuardHttpRecorder(
            TransactionGuardContextRegistry contextRegistry,
            MonotonicClock clock,
            ExternalCallUriSanitizer uriSanitizer,
            ExternalCallRuleMatcher ruleMatcher
    ) {
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.uriSanitizer = Objects.requireNonNull(uriSanitizer, "uriSanitizer must not be null");
        this.ruleMatcher = Objects.requireNonNull(ruleMatcher, "ruleMatcher must not be null");
    }

    /**
     * Returns whether the current transaction is observed by Transaction Guard.
     *
     * @return {@code true} when an HTTP observation can be attached
     */
    public boolean isTransactionObserved() {
        return contextRegistry.currentContext().isPresent();
    }

    /**
     * Returns the current monotonic time used for call duration measurement.
     *
     * @return monotonic nanosecond value
     */
    public long nanoTime() {
        return clock.nanoTime();
    }

    /**
     * Records a successfully completed transport call.
     *
     * @param request sanitized request metadata source
     * @param durationNanos monotonic call duration
     */
    public void recordSuccess(HttpRequest request, long durationNanos) {
        record(request, durationNanos, ExternalCallOutcome.SUCCESS, null);
    }

    /**
     * Records an HTTP error response without reading or buffering its body.
     *
     * @param request sanitized request metadata source
     * @param durationNanos monotonic call duration
     */
    public void recordHttpFailure(HttpRequest request, long durationNanos) {
        record(request, durationNanos, ExternalCallOutcome.FAILURE, null);
    }

    /**
     * Records a call that failed with the supplied original exception.
     *
     * @param request sanitized request metadata source
     * @param durationNanos monotonic call duration
     * @param failure original call failure
     */
    public void recordFailure(HttpRequest request, long durationNanos, Throwable failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        record(request, durationNanos, ExternalCallOutcome.FAILURE, failure.getClass().getName());
    }

    private void record(
            HttpRequest request,
            long durationNanos,
            ExternalCallOutcome outcome,
            String exceptionType
    ) {
        Objects.requireNonNull(request, "request must not be null");
        contextRegistry.currentContext().ifPresent(context -> {
            ExternalCallUriSanitizer.SanitizedDestination destination = uriSanitizer.sanitize(request.getURI());
            ExternalCallObservation observation = new ExternalCallObservation(
                    ExternalClientType.REST_CLIENT,
                    request.getMethod().name(),
                    destination.host(),
                    destination.path(),
                    Math.max(0, durationNanos),
                    outcome,
                    exceptionType
            );
            if (!ruleMatcher.isIgnored(observation)) {
                context.addExternalCall(observation);
            }
        });
    }
}
