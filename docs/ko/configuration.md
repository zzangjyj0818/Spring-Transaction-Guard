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
  violation:
    mode: log
```

| 속성 | 기본값 | 설명 |
|---|---:|---|
| `transaction-guard.enabled` | `true` | Guard 전체 활성화 여부 |
| `transaction-guard.transaction.max-duration` | `2s` | TG001을 발생시키는 트랜잭션 시간 임계값 |
| `transaction-guard.external-call.enabled` | `true` | `RestClient` 관측 활성화 여부 |
| `transaction-guard.external-call.slow-threshold` | `1s` | TG003을 발생시키는 HTTP 호출 시간 임계값 |
| `transaction-guard.violation.mode` | `LOG` | 위반 처리 방식: `LOG` 또는 `THROW` |

Duration에는 Spring Boot가 지원하는 `200ms`, `2s`, `1m` 같은 값을 사용할 수 있습니다. 시간 임계값은 음수일 수 없습니다.

애플리케이션에 사용자 정의 `TransactionGuardReporter` Bean을 등록하면 기본 Reporter 대신 사용됩니다.
