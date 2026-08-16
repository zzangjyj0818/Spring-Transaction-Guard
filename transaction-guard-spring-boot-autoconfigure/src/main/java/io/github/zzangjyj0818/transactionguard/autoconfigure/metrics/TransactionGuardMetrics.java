package io.github.zzangjyj0818.transactionguard.autoconfigure.metrics;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.RedisOperationObservation;
import io.github.zzangjyj0818.transactionguard.core.model.KafkaProducerObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionObservationListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Records low-cardinality Micrometer metrics for completed Guard observations. */
public final class TransactionGuardMetrics implements TransactionObservationListener {

    public static final String TRANSACTION_DURATION = "transaction.guard.transaction.duration";
    public static final String VIOLATION_TOTAL = "transaction.guard.violation.total";
    public static final String EXTERNAL_HTTP_DURATION = "transaction.guard.external.http.duration";
    public static final String EXTERNAL_HTTP_TOTAL = "transaction.guard.external.http.total";
    public static final String REDIS_DURATION = "transaction.guard.redis.duration";
    public static final String REDIS_TOTAL = "transaction.guard.redis.total";
    public static final String KAFKA_PRODUCER_DURATION = "transaction.guard.kafka.producer.duration";
    public static final String KAFKA_PRODUCER_TOTAL = "transaction.guard.kafka.producer.total";
    public static final String JDBC_QUERY_TOTAL = "transaction.guard.jdbc.query.total";
    public static final String JDBC_QUERY_FAILURE_TOTAL = "transaction.guard.jdbc.query.failure.total";
    public static final String JDBC_QUERY_DURATION = "transaction.guard.jdbc.query.duration";

    private final MeterRegistry registry;

    /** Creates a metrics listener backed by the supplied registry. */
    public TransactionGuardMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void onCompleted(TransactionSnapshot snapshot, List<TransactionGuardViolation> violations) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(violations, "violations must not be null");

        registry.timer(TRANSACTION_DURATION, "outcome", tag(snapshot.outcome()))
                .record(snapshot.duration());

        for (TransactionGuardViolation violation : violations) {
            registry.counter(VIOLATION_TOTAL, "code", violation.code()).increment();
        }
        for (ExternalCallObservation call : snapshot.externalCalls()) {
            Tags tags = Tags.of(
                    "client_type", tag(call.clientType()),
                    "outcome", tag(call.outcome()));
            registry.timer(EXTERNAL_HTTP_DURATION, tags)
                    .record(call.durationNanos(), TimeUnit.NANOSECONDS);
            registry.counter(EXTERNAL_HTTP_TOTAL, tags).increment();
        }
        for (RedisOperationObservation operation : snapshot.redisOperations()) {
            Tags tags = Tags.of(
                    "command_category", tag(operation.commandCategory()),
                    "outcome", tag(operation.outcome()));
            registry.timer(REDIS_DURATION, tags)
                    .record(operation.durationNanos(), TimeUnit.NANOSECONDS);
            registry.counter(REDIS_TOTAL, tags).increment();
        }
        for (KafkaProducerObservation call : snapshot.kafkaProducerCalls()) {
            Tags tags = Tags.of("outcome", tag(call.outcome()));
            registry.timer(KAFKA_PRODUCER_DURATION, tags)
                    .record(call.durationNanos(), TimeUnit.NANOSECONDS);
            registry.counter(KAFKA_PRODUCER_TOTAL, tags).increment();
        }
        if (snapshot.jdbcQueries().queryCount() > 0) {
            registry.counter(JDBC_QUERY_TOTAL).increment(snapshot.jdbcQueries().queryCount());
            registry.counter(JDBC_QUERY_FAILURE_TOTAL).increment(snapshot.jdbcQueries().failedQueryCount());
            registry.timer(JDBC_QUERY_DURATION).record(
                    snapshot.jdbcQueries().totalDurationNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
