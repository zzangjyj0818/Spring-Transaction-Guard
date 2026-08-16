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
| `transaction-guard.violation.mode` | `LOG` | 위반 처리 방식: `LOG` 또는 `THROW` |
| `transaction-guard.violation.disabled-codes` | `[]` | 비활성화할 `TG001`, `TG002`, `TG003` 코드 |

Duration에는 Spring Boot가 지원하는 `200ms`, `2s`, `1m` 같은 값을 사용할 수 있습니다. 시간 임계값은 음수일 수 없습니다.

애플리케이션에 사용자 정의 `TransactionGuardReporter` Bean을 등록하면 기본 Reporter 대신 사용됩니다.

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
