# Observability and Prometheus

English | [한국어](../ko/observability.md)

Starting with v0.3, Spring Transaction Guard optionally provides Micrometer metrics and a read-only Actuator endpoint. The library does not force a Prometheus registry. The application chooses its registry and Actuator exposure policy.

## Dependencies

Add Actuator and the desired Micrometer registry alongside the starter.

```kotlin
dependencies {
    implementation("io.github.zzangjyj0818:transaction-guard-spring-boot-starter:<version>")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
```

Transaction Guard does not create its metrics bean or Actuator endpoint when no `MeterRegistry` bean is available.

## Metrics

| Micrometer name | Type | Recorded | Low-cardinality tags |
|---|---|---|---|
| `transaction.guard.transaction.duration` | Timer | Once per observed transaction completion | `outcome`: `committed`, `rolled_back`, `unknown` |
| `transaction.guard.violation.total` | Counter | Once per evaluated violation | `code`: `TG001`–`TG008` |
| `transaction.guard.external.http.duration` | Timer | Once per observed external HTTP call | `client_type`, `outcome` |
| `transaction.guard.external.http.total` | Counter | Once per observed external HTTP call | `client_type`, `outcome` |
| `transaction.guard.redis.duration` | Timer | Once per observed Redis operation | `command_category`, `outcome` |
| `transaction.guard.redis.total` | Counter | Once per observed Redis operation | `command_category`, `outcome` |
| `transaction.guard.kafka.producer.duration` | Timer | Once per Kafka send completed in the transaction | `outcome` |
| `transaction.guard.kafka.producer.total` | Counter | Once per Kafka send completed in the transaction | `outcome` |
| `transaction.guard.jdbc.query.total` | Counter | Incremented by the completed transaction's JDBC query count | none |
| `transaction.guard.jdbc.query.failure.total` | Counter | Incremented by failed JDBC query count | none |
| `transaction.guard.jdbc.query.duration` | Timer | One aggregate per transaction containing JDBC queries | none |

`client_type` is `rest_client` or `open_feign`; the external call `outcome` is `success` or `failure`. Timer base units and concrete Prometheus suffixes follow the Micrometer registry conventions.

Transaction IDs, entry methods, hosts, paths, Redis keys, Kafka topics, SQL, and exception types are not metric tags. Request queries, headers, bodies, Kafka payloads, and bind parameters are not collected. The number of time series therefore does not grow with user input or destination count.

Calls excluded by an Ignore rule are not recorded as metrics. Calls matched by an Allow rule remain in observation metrics but do not increment TG002/TG003 violation counters. A disabled violation code does not increment its counter.

## Actuator endpoint

Declare both the Spring Boot 4.1 access and exposure policies.

```yaml
management:
  endpoint:
    transactionguard:
      access: READ_ONLY
  endpoints:
    web:
      exposure:
        include: health,prometheus,transactionguard
```

`GET /actuator/transactionguard` then returns:

- Timer summaries grouped by transaction outcome
- TG001–TG008 violation counts
- HTTP summaries grouped by RestClient/OpenFeign and success/failure
- Redis summaries grouped by command category and outcome
- Kafka producer summaries grouped by outcome
- JDBC query/failure counts and aggregate duration
- Effective thresholds, Query Budget, and violation mode
- Disabled violation codes and counts of Ignore/Allow rules

The endpoint never returns configured host/path pattern values and does not retain a recent-event list. Every dimension is bounded by fixed enums and violation codes.

Protect management endpoints with authentication and network policy; do not expose them directly to the public internet. Omitting `transactionguard` from exposure or setting its access to `NONE` disables access.

## Prometheus example

Run the example application:

```bash
./gradlew :transaction-guard-example:bootRun
```

Trigger the risk scenarios from another terminal:

```bash
curl http://localhost:8080/guard/tg001
curl http://localhost:8080/guard/tg002
curl http://localhost:8080/guard/tg003
curl http://localhost:8080/guard/feign
curl http://localhost:8080/actuator/transactionguard
curl http://localhost:8080/actuator/prometheus
```

`transaction-guard-example/prometheus.yml` is a minimal configuration for Prometheus running in Docker to scrape the example on the host.

```bash
docker run --rm -p 9090:9090 \
  -v "$PWD/transaction-guard-example/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  prom/prometheus
```

Example PromQL:

```promql
rate(transaction_guard_violation_total[5m])
rate(transaction_guard_external_http_total[5m])
rate(transaction_guard_redis_total[5m])
rate(transaction_guard_kafka_producer_total[5m])
rate(transaction_guard_jdbc_query_total[5m])
histogram_quantile(0.95, sum by (le) (rate(transaction_guard_transaction_duration_seconds_bucket[5m])))
```

Timer histogram buckets may need to be enabled through the application's Micrometer distribution settings.
