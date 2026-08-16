# Using Redis, Kafka, and JDBC Observation

[Documentation home](README.md) | [한국어](../ko/io-observation.md)

Transaction Guard conditionally observes Spring Data Redis, Spring Kafka, and JDBC calls already made by an application. No enable annotation is required. An observation is attached only while an actual Spring transaction is active and a Guard context is bound to it.

## Common setup

Add the starter and only the client dependencies your application uses.

```kotlin
dependencies {
    implementation("io.github.zzangjyj0818:transaction-guard-spring-boot-starter:<version>")

    // Add only what the application uses.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
```

Redis and Kafka are optional dependencies for Transaction Guard. Their auto-configuration and policy beans are absent when the corresponding client is not on the classpath.

## Redis

### How to use it

Use an imperative Spring-managed `RedisTemplate` or a Spring Data Redis Operations API such as `ValueOperations` normally.

```java
@Service
class OrderService {
    private final StringRedisTemplate redis;

    OrderService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Transactional
    public void createOrder(String orderId) {
        // The active database transaction makes this a TG004 candidate.
        redis.opsForValue().get("order:" + orderId);
    }
}
```

By default, every observed Redis operation produces TG004. An operation exceeding `slow-threshold` also produces TG005.

```yaml
transaction-guard:
  redis:
    enabled: true
    slow-threshold: 200ms
```

The observation contains only client type, a READ/WRITE/DELETE/BATCH/SCRIPT/OTHER category, duration, outcome, and exception class name. Keys, values, command arguments, Redis URIs, credentials, and exception messages are never stored.

Reactive Redis is not supported. Calls through `ReactiveRedisTemplate` are not automatically observed.

## Kafka producer

### How to use it

Use the `send` methods of a Spring-managed `KafkaTemplate` normally.

```java
@Transactional
public void saveAndPublish(Order order) {
    repository.save(order);
    kafkaTemplate.send("orders", order.id(), order).join();
}
```

```yaml
transaction-guard:
  kafka:
    enabled: true
    slow-threshold: 500ms
```

When the future returned by `send` completes while the transaction is still observed, its result is evaluated by TG006/TG007. Code that waits with `join()` or `get()` therefore records success or failure in the transaction snapshot.

```java
// Completion happens before transaction completion and can be observed.
kafkaTemplate.send("orders", event).join();
```

A future completed after transaction completion is not added retroactively to an already completed snapshot. This is not guaranteed broker-completion detection for fire-and-forget sends. Transaction Guard neither replaces the returned future nor reads its payload.

Kafka observation contains only duration, outcome, and exception class name. Topic, key, value/payload, and headers are excluded. Consumers and reactive messaging are not supported.

## JDBC query count

### How to use it

No application change is needed when `JdbcTemplate`, JPA, or another JDBC client uses a Spring-managed `DataSource`. Transaction Guard instruments the returned Connection and Statement objects.

```java
@Transactional(readOnly = true)
public OrderSummary loadSummary(long orderId) {
    return jdbcTemplate.queryForObject(
            "select id, status from orders where id = ?",
            mapper,
            orderId);
}
```

```yaml
transaction-guard:
  jdbc:
    enabled: true
```

The following Statement execution APIs increment the query count:

- `executeQuery`
- `executeUpdate` and `executeLargeUpdate`
- `execute`
- `executeBatch` and `executeLargeBatch`

One execute-method invocation counts once. A batch is counted by the `executeBatch()` invocation, not by the number of SQL entries in it. Statement creation, parameter binding, `addBatch`, and result access do not increment the count.

Only query count, failure count, and aggregate duration are retained. SQL text, bind parameters, database URLs, usernames, and credentials are never stored.

`Connection.unwrap(Connection.class)` and `Statement.unwrap(...)` cannot silently bypass the Guard proxy. Unwrapping another vendor-specific type follows the driver behavior; queries executed directly through vendor APIs after such an unwrap may be outside the supported boundary.

## Experimental Query Budget

Enable JDBC observation, then explicitly opt in to Query Budget.

```yaml
transaction-guard:
  jdbc:
    enabled: true
  query-budget:
    enabled: true
    max-queries: 25
```

A transaction may execute up to 25 queries; TG008 is reported starting with query 26. `max-queries: 0` disallows every JDBC query. Query Budget is currently a global default and does not support method-level or annotation-level overrides.

LOG mode reports the excess and preserves the business outcome. THROW mode evaluates TG008 before commit and can enforce the budget in tests and CI.

## Propagation and transaction boundaries

- Nested REQUIRED calls accumulate in one snapshot.
- REQUIRES_NEW has an independent snapshot and count.
- Redis, Kafka, and JDBC work in a NOT_SUPPORTED section is not attached to the outer snapshot.
- Work outside a transaction is not recorded.

## Troubleshooting

1. Verify that `transaction-guard.enabled` and the feature-specific `enabled` property are `true`.
2. Verify that an actual transaction is active at the call site (`TransactionSynchronizationManager.isActualTransactionActive()`).
3. Check whether self-invocation bypasses the `@Transactional` proxy.
4. Ensure the Redis, Kafka, or DataSource object is created within the Spring bean lifecycle.
5. For Kafka, verify that the future completes before transaction completion.
6. Check that the code is not listed in `transaction-guard.violation.disabled-codes`.
7. With Actuator and Micrometer enabled, use the [Observability guide](observability.md) to confirm that counts increase.
