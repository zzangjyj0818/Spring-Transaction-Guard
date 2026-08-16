# 설정 레퍼런스

[문서 홈](README.md) | [English](../en/configuration.md)

```yaml
transaction-guard:
  enabled: true
  transaction:
    max-duration: 2s
  external-call:
    enabled: true
    slow-threshold: 1s
    ignore-hosts: []
    ignore-endpoints: []
    allow-hosts: []
    allow-endpoints: []
  redis:
    enabled: true
    slow-threshold: 1s
  kafka:
    enabled: true
    slow-threshold: 1s
  jdbc:
    enabled: true
  query-budget:
    enabled: false
    max-queries: 100
  violation:
    mode: log
    disabled-codes: []
```

| 속성 | 기본값 | 설명 |
|---|---:|---|
| `transaction-guard.enabled` | `true` | Guard 전체 활성화 여부 |
| `transaction-guard.transaction.max-duration` | `2s` | TG001을 발생시키는 트랜잭션 시간 임계값 |
| `transaction-guard.external-call.enabled` | `true` | 지원 HTTP client 관측 활성화 여부 |
| `transaction-guard.external-call.slow-threshold` | `1s` | TG003을 발생시키는 HTTP 호출 시간 임계값 |
| `transaction-guard.external-call.ignore-hosts` | `[]` | 기록하지 않을 host glob |
| `transaction-guard.external-call.ignore-endpoints` | `[]` | 기록하지 않을 `host/path` glob |
| `transaction-guard.external-call.allow-hosts` | `[]` | 기록하되 TG002/TG003에서 제외할 host glob |
| `transaction-guard.external-call.allow-endpoints` | `[]` | 기록하되 TG002/TG003에서 제외할 `host/path` glob |
| `transaction-guard.redis.enabled` | `true` | 명령형 Spring Data Redis 관측 활성화 여부 |
| `transaction-guard.redis.slow-threshold` | `1s` | TG005를 발생시키는 Redis 호출 시간 임계값 |
| `transaction-guard.kafka.enabled` | `true` | KafkaTemplate producer 호출 관측 활성화 여부 |
| `transaction-guard.kafka.slow-threshold` | `1s` | TG007을 발생시키는 producer 호출 시간 임계값 |
| `transaction-guard.jdbc.enabled` | `true` | JDBC query count 관측 활성화 여부 |
| `transaction-guard.query-budget.enabled` | `false` | 실험적 TG008 Query Budget 활성화 여부 |
| `transaction-guard.query-budget.max-queries` | `100` | 한 트랜잭션에서 허용할 최대 JDBC query 수 |
| `transaction-guard.violation.mode` | `LOG` | 위반 처리 방식: `LOG` 또는 `THROW` |
| `transaction-guard.violation.disabled-codes` | `[]` | 비활성화할 `TG001`~`TG008` 코드 |

Duration에는 Spring Boot가 지원하는 `200ms`, `2s`, `1m` 같은 값을 사용할 수 있습니다. 시간 임계값은 음수일 수 없습니다.

애플리케이션에 사용자 정의 `TransactionGuardReporter` Bean을 등록하면 기본 Reporter 대신 사용됩니다.

## 실험적 Query Budget

Query Budget은 기본적으로 꺼져 있습니다. 활성화하면 트랜잭션 안에서 실행된 JDBC query 수가 `max-queries`와 같을 때까지는 허용하고, 초과하면 TG008을 보고합니다. `0`은 모든 JDBC query를 금지한다는 의미이며 음수는 허용되지 않습니다.

```yaml
transaction-guard:
  query-budget:
    enabled: true
    max-queries: 25
```

이 기능은 SQL 원문, bind parameter, DB URL 또는 credential을 수집하지 않습니다. 전역 예산만 지원하는 실험적 기능이며 SQL fingerprint 기반 N+1 판별은 제공하지 않습니다.

## 규칙 동작

규칙에서 `*`는 길이 제한 없는 문자열, `?`는 한 글자를 의미합니다. Host는 대소문자를 구분하지 않고 trailing dot을 제거한 뒤 비교합니다. Endpoint 규칙은 정제된 `host/path`를 사용하며 query string과 fragment는 매칭에 참여하지 않습니다.

```yaml
transaction-guard:
  external-call:
    ignore-hosts:
      - "*.metadata.internal"
    ignore-endpoints:
      - "health.internal/actuator/*"
    allow-hosts:
      - "payments.internal"
    allow-endpoints:
      - "audit.internal/events/?"
  violation:
    disabled-codes:
      - TG003
```

Ignore는 Allow보다 우선합니다. Ignore 호출은 transaction Snapshot에 남지 않습니다. Allow 호출은 관측 정보에는 남지만 TG002와 TG003을 생성하지 않습니다. 비활성화한 코드는 평가하지 않습니다. 모든 목록이 비어 있으면 v0.1과 동일하게 동작합니다.
