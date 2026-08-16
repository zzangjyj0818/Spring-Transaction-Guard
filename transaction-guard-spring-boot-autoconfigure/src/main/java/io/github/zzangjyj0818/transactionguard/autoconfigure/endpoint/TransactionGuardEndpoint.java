package io.github.zzangjyj0818.transactionguard.autoconfigure.endpoint;

import io.github.zzangjyj0818.transactionguard.autoconfigure.TransactionGuardProperties;
import io.github.zzangjyj0818.transactionguard.autoconfigure.metrics.TransactionGuardMetrics;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.model.RedisCommandCategory;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.Access;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Read-only, bounded operational snapshot for Transaction Guard. */
@Endpoint(id = "transactionguard", defaultAccess = Access.READ_ONLY)
public final class TransactionGuardEndpoint {

    private final MeterRegistry registry;
    private final TransactionGuardProperties properties;

    /** Creates the endpoint from the metrics registry and sanitized Guard settings. */
    public TransactionGuardEndpoint(MeterRegistry registry, TransactionGuardProperties properties) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** Returns a fresh immutable snapshot without raw destination or request data. */
    @ReadOperation
    public Snapshot snapshot() {
        return new Snapshot(configuration(), metrics());
    }

    private Configuration configuration() {
        TransactionGuardProperties.ExternalCall externalCall = properties.getExternalCall();
        Set<String> disabledCodes = properties.getViolation().getDisabledCodes().stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        return new Configuration(
                properties.isEnabled(),
                properties.getViolation().getMode().name().toLowerCase(Locale.ROOT),
                properties.getTransaction().getMaxDuration().toNanos(),
                externalCall.isEnabled(),
                externalCall.getSlowThreshold().toNanos(),
                properties.getRedis().isEnabled(),
                properties.getRedis().getSlowThreshold().toNanos(),
                properties.getKafka().isEnabled(),
                properties.getKafka().getSlowThreshold().toNanos(),
                properties.getJdbc().isEnabled(),
                properties.getQueryBudget().isEnabled(),
                properties.getQueryBudget().getMaxQueries(),
                disabledCodes,
                new RuleCounts(
                        externalCall.getIgnoreHosts().size(),
                        externalCall.getIgnoreEndpoints().size(),
                        externalCall.getAllowHosts().size(),
                        externalCall.getAllowEndpoints().size()));
    }

    private Metrics metrics() {
        Map<String, TimerValue> transactions = new LinkedHashMap<>();
        for (TransactionOutcome outcome : TransactionOutcome.values()) {
            String value = tag(outcome);
            transactions.put(value, timer(
                    TransactionGuardMetrics.TRANSACTION_DURATION, "outcome", value));
        }

        Map<String, Long> violations = new LinkedHashMap<>();
        for (TransactionGuardProperties.ViolationCode code : TransactionGuardProperties.ViolationCode.values()) {
            Counter counter = registry.find(TransactionGuardMetrics.VIOLATION_TOTAL)
                    .tag("code", code.name()).counter();
            violations.put(code.name(), counter == null ? 0L : Math.round(counter.count()));
        }

        Map<String, ExternalHttpValue> externalHttp = new LinkedHashMap<>();
        for (ExternalClientType clientType : ExternalClientType.values()) {
            for (ExternalCallOutcome outcome : ExternalCallOutcome.values()) {
                String client = tag(clientType);
                String result = tag(outcome);
                Counter counter = registry.find(TransactionGuardMetrics.EXTERNAL_HTTP_TOTAL)
                        .tags("client_type", client, "outcome", result).counter();
                externalHttp.put(client + ":" + result, new ExternalHttpValue(
                        counter == null ? 0L : Math.round(counter.count()),
                        timer(TransactionGuardMetrics.EXTERNAL_HTTP_DURATION,
                                "client_type", client, "outcome", result)));
            }
        }
        Map<String, ExternalHttpValue> redis = new LinkedHashMap<>();
        for (RedisCommandCategory category : RedisCommandCategory.values()) {
            for (ExternalCallOutcome outcome : ExternalCallOutcome.values()) {
                String command = tag(category);
                String result = tag(outcome);
                Counter counter = registry.find(TransactionGuardMetrics.REDIS_TOTAL)
                        .tags("command_category", command, "outcome", result).counter();
                redis.put(command + ":" + result, new ExternalHttpValue(
                        counter == null ? 0L : Math.round(counter.count()),
                        timer(TransactionGuardMetrics.REDIS_DURATION,
                                "command_category", command, "outcome", result)));
            }
        }
        Map<String, ExternalHttpValue> kafka = new LinkedHashMap<>();
        for (ExternalCallOutcome outcome : ExternalCallOutcome.values()) {
            String result = tag(outcome);
            Counter counter = registry.find(TransactionGuardMetrics.KAFKA_PRODUCER_TOTAL)
                    .tag("outcome", result).counter();
            kafka.put(result, new ExternalHttpValue(
                    counter == null ? 0L : Math.round(counter.count()),
                    timer(TransactionGuardMetrics.KAFKA_PRODUCER_DURATION, "outcome", result)));
        }
        Counter jdbcTotal = registry.find(TransactionGuardMetrics.JDBC_QUERY_TOTAL).counter();
        Counter jdbcFailures = registry.find(TransactionGuardMetrics.JDBC_QUERY_FAILURE_TOTAL).counter();
        JdbcValue jdbc = new JdbcValue(
                jdbcTotal == null ? 0L : Math.round(jdbcTotal.count()),
                jdbcFailures == null ? 0L : Math.round(jdbcFailures.count()),
                timer(TransactionGuardMetrics.JDBC_QUERY_DURATION));
        return new Metrics(Map.copyOf(transactions), Map.copyOf(violations),
                Map.copyOf(externalHttp), Map.copyOf(redis), Map.copyOf(kafka), jdbc);
    }

    private TimerValue timer(String name, String... tags) {
        Timer timer = registry.find(name).tags(tags).timer();
        if (timer == null) {
            return new TimerValue(0, 0, 0);
        }
        return new TimerValue(
                timer.count(),
                Math.round(timer.totalTime(TimeUnit.NANOSECONDS)),
                Math.round(timer.max(TimeUnit.NANOSECONDS)));
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** Complete endpoint response. */
    public record Snapshot(Configuration configuration, Metrics metrics) {
    }

    /** Sanitized effective Guard configuration. */
    public record Configuration(
            boolean enabled,
            String violationMode,
            long transactionMaxDurationNanos,
            boolean externalCallEnabled,
            long externalCallSlowThresholdNanos,
            boolean redisEnabled,
            long redisSlowThresholdNanos,
            boolean kafkaEnabled,
            long kafkaSlowThresholdNanos,
            boolean jdbcEnabled,
            boolean queryBudgetEnabled,
            long queryBudgetMaxQueries,
            Set<String> disabledViolationCodes,
            RuleCounts ruleCounts
    ) {
    }

    /** Counts of configured destination rules; pattern values are intentionally omitted. */
    public record RuleCounts(int ignoredHosts, int ignoredEndpoints, int allowedHosts, int allowedEndpoints) {
    }

    /** Fixed-cardinality metrics snapshot. */
    public record Metrics(
            Map<String, TimerValue> transactions,
            Map<String, Long> violations,
            Map<String, ExternalHttpValue> externalHttp,
            Map<String, ExternalHttpValue> redis,
            Map<String, ExternalHttpValue> kafkaProducer,
            JdbcValue jdbc
    ) {
    }

    /** Timer summary expressed in nanoseconds. */
    public record TimerValue(long count, long totalTimeNanos, long maxNanos) {
    }

    /** External HTTP count and duration summary. */
    public record ExternalHttpValue(long count, TimerValue duration) {
    }

    /** Aggregate JDBC execution summary. */
    public record JdbcValue(long queryCount, long failedQueryCount, TimerValue duration) {
    }
}
