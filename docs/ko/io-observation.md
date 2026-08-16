# Redis, Kafka, JDBC 관측 사용법

[문서 홈](README.md) | [English](../en/io-observation.md)

Transaction Guard는 애플리케이션에 이미 존재하는 Spring Data Redis, Spring Kafka 및 JDBC 호출을 조건부로 관측합니다. 별도의 활성화 애노테이션은 필요하지 않습니다. 관측 결과는 실제 Spring 트랜잭션이 활성이고 Guard Context가 연결된 동안에만 해당 트랜잭션 Snapshot에 추가됩니다.

## 공통 준비

Starter와 애플리케이션에서 사용할 client 의존성을 추가합니다.

```kotlin
dependencies {
    implementation("io.github.zzangjyj0818:transaction-guard-spring-boot-starter:<version>")

    // 사용하는 항목만 추가합니다.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
```

Transaction Guard의 Redis/Kafka 의존성은 선택 사항입니다. 관련 client가 classpath에 없으면 해당 자동 구성과 정책 Bean은 생성되지 않습니다.

## Redis

### 적용 방법

Spring Bean으로 관리되는 명령형 `RedisTemplate`과 `ValueOperations` 같은 Spring Data Redis Operations API를 평소처럼 사용합니다.

```java
@Service
class OrderService {
    private final StringRedisTemplate redis;

    OrderService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Transactional
    public void createOrder(String orderId) {
        // 활성 DB 트랜잭션 안이므로 TG004 평가 대상입니다.
        redis.opsForValue().get("order:" + orderId);
    }
}
```

기본 설정에서는 모든 관측된 Redis 작업이 TG004를 만들고, duration이 `slow-threshold`를 초과하면 TG005도 만듭니다.

```yaml
transaction-guard:
  redis:
    enabled: true
    slow-threshold: 200ms
```

관측 데이터에는 client type, READ/WRITE/DELETE/BATCH/SCRIPT/OTHER category, duration, 결과와 예외 class 이름만 포함됩니다. key, value, 명령 인자, Redis URI, credential 및 예외 메시지는 저장하지 않습니다.

Reactive Redis는 지원하지 않습니다. `ReactiveRedisTemplate`을 사용하면 자동 관측되지 않습니다.

## Kafka producer

### 적용 방법

Spring Bean으로 관리되는 `KafkaTemplate`의 `send` 계열 메서드를 그대로 사용합니다.

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

`send`가 반환한 future가 Guard가 관측 중인 트랜잭션 안에서 완료되면 TG006/TG007 평가에 사용됩니다. `join()` 또는 `get()`으로 완료를 기다리는 코드는 성공과 실패가 트랜잭션 Snapshot에 반영됩니다.

```java
// future가 transaction 종료 전에 완료되므로 결과를 관측할 수 있습니다.
kafkaTemplate.send("orders", event).join();
```

future가 트랜잭션 종료 후 완료되면 이미 완성된 Snapshot에 결과를 소급해서 추가하지 않습니다. 따라서 fire-and-forget 호출의 broker 완료 결과를 항상 탐지하는 기능은 아닙니다. Transaction Guard는 반환한 future를 교체하거나 payload를 읽지 않습니다.

Kafka 관측 데이터에는 duration, 결과와 예외 class 이름만 포함됩니다. topic, key, value/payload 및 header는 저장하지 않습니다. Consumer와 reactive messaging은 지원하지 않습니다.

## JDBC query count

### 적용 방법

Spring Bean으로 관리되는 `DataSource`를 `JdbcTemplate`, JPA 또는 JDBC client가 사용하면 별도 코드 없이 Connection과 Statement가 계측됩니다.

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

다음 Statement 실행 API가 query count를 증가시킵니다.

- `executeQuery`
- `executeUpdate`와 `executeLargeUpdate`
- `execute`
- `executeBatch`와 `executeLargeBatch`

하나의 execute 메서드 호출은 한 번으로 계산됩니다. Batch 안의 SQL 항목 수가 아니라 `executeBatch()` 호출 수를 계산합니다. Statement 생성, parameter 설정, `addBatch`, result 조회는 count를 증가시키지 않습니다.

관측 데이터는 query count, 실패 count와 총 duration뿐입니다. SQL 원문, bind parameter, DB URL, username 및 credential은 저장하지 않습니다.

Transaction Guard는 `Connection.unwrap(Connection.class)`과 `Statement.unwrap(...)`이 Guard proxy를 우회하지 않도록 처리합니다. 다른 vendor 전용 type을 unwrap하면 원본 driver 동작을 따르므로, unwrap 이후 vendor API로 직접 실행하는 쿼리는 지원 범위에 포함되지 않을 수 있습니다.

## 실험적 Query Budget

JDBC 관측을 활성화한 다음 Query Budget을 명시적으로 켭니다.

```yaml
transaction-guard:
  jdbc:
    enabled: true
  query-budget:
    enabled: true
    max-queries: 25
```

한 트랜잭션의 query count가 25 이하이면 허용하고 26부터 TG008을 보고합니다. `max-queries: 0`은 JDBC query를 하나도 허용하지 않습니다. Query Budget은 전역 기본값이며 메서드별 또는 애노테이션별 override는 아직 지원하지 않습니다.

LOG 모드에서는 초과 사실을 보고한 뒤 비즈니스 결과를 유지합니다. THROW 모드에서는 commit 전에 TG008을 평가하므로 테스트와 CI에서 query 예산을 강제할 수 있습니다.

## 전파와 트랜잭션 경계

- REQUIRED 중첩 호출은 같은 Snapshot에 누적됩니다.
- REQUIRES_NEW는 외부 트랜잭션과 별도 Snapshot 및 count를 가집니다.
- NOT_SUPPORTED 구간의 Redis/Kafka/JDBC 작업은 외부 Snapshot에 포함되지 않습니다.
- 트랜잭션 밖의 작업은 기록하지 않습니다.

## 동작하지 않을 때 확인할 항목

1. `transaction-guard.enabled`와 기능별 `enabled`가 `true`인지 확인합니다.
2. 호출 시점에 `TransactionSynchronizationManager.isActualTransactionActive()`가 참인 실제 트랜잭션인지 확인합니다.
3. self-invocation 때문에 `@Transactional` proxy를 우회하지 않았는지 확인합니다.
4. Redis/Kafka/DataSource 객체가 Spring Bean lifecycle 안에서 생성됐는지 확인합니다.
5. Kafka future가 transaction 종료 전에 완료됐는지 확인합니다.
6. `transaction-guard.violation.disabled-codes`에 해당 코드가 없는지 확인합니다.
7. Actuator와 Micrometer를 사용한다면 [관측성 가이드](observability.md)에서 count가 증가하는지 확인합니다.
