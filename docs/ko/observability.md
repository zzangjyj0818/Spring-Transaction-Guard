# 관측성과 Prometheus

[English](../en/observability.md) | 한국어

v0.3부터 Spring Transaction Guard는 Micrometer 메트릭과 읽기 전용 Actuator endpoint를 선택적으로 제공합니다. 라이브러리는 Prometheus registry를 강제하지 않습니다. 애플리케이션이 사용할 registry와 Actuator 노출 범위를 직접 선택합니다.

## 의존성

Starter와 함께 Actuator 및 원하는 Micrometer registry를 추가합니다.

```kotlin
dependencies {
    implementation("io.github.zzangjyj0818:transaction-guard-spring-boot-starter:<version>")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
```

`MeterRegistry` Bean이 없으면 Transaction Guard 메트릭 Bean과 Actuator endpoint는 생성되지 않습니다.

## 메트릭

| Micrometer 이름 | 타입 | 기록 시점 | 저카디널리티 태그 |
|---|---|---|---|
| `transaction.guard.transaction.duration` | Timer | 관측된 트랜잭션 완료 시 1회 | `outcome`: `committed`, `rolled_back`, `unknown` |
| `transaction.guard.violation.total` | Counter | 평가된 위반마다 1회 | `code`: `TG001`~`TG008` |
| `transaction.guard.external.http.duration` | Timer | 관측된 외부 HTTP 호출마다 1회 | `client_type`, `outcome` |
| `transaction.guard.external.http.total` | Counter | 관측된 외부 HTTP 호출마다 1회 | `client_type`, `outcome` |
| `transaction.guard.redis.duration` | Timer | 관측된 Redis 작업마다 1회 | `command_category`, `outcome` |
| `transaction.guard.redis.total` | Counter | 관측된 Redis 작업마다 1회 | `command_category`, `outcome` |
| `transaction.guard.kafka.producer.duration` | Timer | transaction 안에서 완료된 Kafka send마다 1회 | `outcome` |
| `transaction.guard.kafka.producer.total` | Counter | transaction 안에서 완료된 Kafka send마다 1회 | `outcome` |
| `transaction.guard.jdbc.query.total` | Counter | 완료된 transaction의 JDBC query 수만큼 | 없음 |
| `transaction.guard.jdbc.query.failure.total` | Counter | 실패한 JDBC query 수만큼 | 없음 |
| `transaction.guard.jdbc.query.duration` | Timer | JDBC query가 있는 transaction마다 aggregate 1회 | 없음 |

`client_type`은 `rest_client` 또는 `open_feign`, 외부 호출 `outcome`은 `success` 또는 `failure`입니다. Timer의 기본 시간 단위와 Prometheus에 노출되는 실제 suffix는 Micrometer registry 규칙을 따릅니다.

transaction ID, 진입 메서드, host, path, Redis key, Kafka topic, SQL, 예외 타입은 메트릭 태그로 사용하지 않습니다. 요청 query, header, body, Kafka payload와 bind parameter도 수집하지 않습니다. 따라서 사용자 입력이나 목적지 수에 비례해 시계열 수가 증가하지 않습니다.

Ignore 규칙으로 제외된 호출은 메트릭에도 기록되지 않습니다. Allow 규칙의 호출은 관측 메트릭에는 포함되지만 TG002/TG003 위반 Counter는 증가시키지 않습니다. 비활성화한 위반 코드도 Counter를 증가시키지 않습니다.

## Actuator endpoint

Spring Boot 4.1의 접근 및 노출 정책을 모두 명시합니다.

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

이후 `GET /actuator/transactionguard`에서 다음 정보를 확인할 수 있습니다.

- 트랜잭션 결과별 Timer 집계
- TG001~TG008 위반 횟수
- RestClient/OpenFeign 및 성공/실패별 HTTP 집계
- category/outcome별 Redis 집계
- 성공/실패별 Kafka producer 집계
- JDBC query/실패 count와 aggregate duration
- 적용 중인 시간 임계값, Query Budget과 위반 모드
- 비활성 위반 코드와 Ignore/Allow 규칙 개수

endpoint는 설정된 host/path 패턴의 원문을 반환하지 않으며 별도의 최근 이벤트 목록을 저장하지 않습니다. 모든 차원은 고정된 enum과 위반 코드 조합으로 제한됩니다.

관리 endpoint는 운영망에서 인증·인가하고 외부에 직접 공개하지 마세요. `transactionguard`를 exposure 목록에 넣지 않거나 access를 `NONE`으로 설정하면 사용할 수 없습니다.

## Prometheus 예제

예제 애플리케이션을 실행합니다.

```bash
./gradlew :transaction-guard-example:bootRun
```

다른 터미널에서 위험 시나리오를 실행합니다.

```bash
curl http://localhost:8080/guard/tg001
curl http://localhost:8080/guard/tg002
curl http://localhost:8080/guard/tg003
curl http://localhost:8080/guard/feign
curl http://localhost:8080/actuator/transactionguard
curl http://localhost:8080/actuator/prometheus
```

저장소의 `transaction-guard-example/prometheus.yml`은 Docker에서 실행하는 Prometheus가 호스트의 예제 애플리케이션을 scrape하는 최소 설정입니다.

```bash
docker run --rm -p 9090:9090 \
  -v "$PWD/transaction-guard-example/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  prom/prometheus
```

PromQL 예시:

```promql
rate(transaction_guard_violation_total[5m])
rate(transaction_guard_external_http_total[5m])
rate(transaction_guard_redis_total[5m])
rate(transaction_guard_kafka_producer_total[5m])
rate(transaction_guard_jdbc_query_total[5m])
histogram_quantile(0.95, sum by (le) (rate(transaction_guard_transaction_duration_seconds_bucket[5m])))
```

Timer histogram bucket은 애플리케이션의 Micrometer 분포 설정에 따라 활성화해야 할 수 있습니다.
