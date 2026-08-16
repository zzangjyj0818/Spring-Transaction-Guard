# Configuration Reference

[Documentation home](README.md) | [한국어](../ko/configuration.md)

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

| Property | Default | Description |
|---|---:|---|
| `transaction-guard.enabled` | `true` | Enables Transaction Guard globally |
| `transaction-guard.transaction.max-duration` | `2s` | Transaction duration threshold for TG001 |
| `transaction-guard.external-call.enabled` | `true` | Enables supported HTTP client observation |
| `transaction-guard.external-call.slow-threshold` | `1s` | HTTP call duration threshold for TG003 |
| `transaction-guard.external-call.ignore-hosts` | `[]` | Host globs that are not recorded |
| `transaction-guard.external-call.ignore-endpoints` | `[]` | `host/path` globs that are not recorded |
| `transaction-guard.external-call.allow-hosts` | `[]` | Host globs recorded but excluded from TG002/TG003 |
| `transaction-guard.external-call.allow-endpoints` | `[]` | `host/path` globs recorded but excluded from TG002/TG003 |
| `transaction-guard.redis.enabled` | `true` | Enables imperative Spring Data Redis observation |
| `transaction-guard.redis.slow-threshold` | `1s` | Redis duration threshold for TG005 |
| `transaction-guard.kafka.enabled` | `true` | Enables KafkaTemplate producer-call observation |
| `transaction-guard.kafka.slow-threshold` | `1s` | Producer-call duration threshold for TG007 |
| `transaction-guard.jdbc.enabled` | `true` | Enables JDBC query-count observation |
| `transaction-guard.query-budget.enabled` | `false` | Enables experimental TG008 Query Budget |
| `transaction-guard.query-budget.max-queries` | `100` | Maximum JDBC queries allowed in one transaction |
| `transaction-guard.violation.mode` | `LOG` | Violation handling mode: `LOG` or `THROW` |
| `transaction-guard.violation.disabled-codes` | `[]` | Disabled codes from `TG001` through `TG008` |

Durations accept Spring Boot formats such as `200ms`, `2s`, and `1m`. Duration thresholds cannot be negative.

Register a custom `TransactionGuardReporter` bean to replace the default reporter.

## Experimental Query Budget

Query Budget is disabled by default. When enabled, a transaction may execute up to and including `max-queries`; TG008 is reported only when the count is exceeded. A value of `0` disallows every JDBC query, and negative values are rejected.

```yaml
transaction-guard:
  query-budget:
    enabled: true
    max-queries: 25
```

The feature never collects SQL text, bind parameters, database URLs, or credentials. It is an experimental global budget and does not attempt SQL-fingerprint-based N+1 classification.

## Rule behavior

Rules accept `*` for any character sequence and `?` for one character. Hosts are matched case-insensitively after removing a trailing dot. Endpoint rules use sanitized `host/path`; query strings and fragments never participate in matching.

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

Ignore takes precedence over allow. An ignored call is absent from the transaction snapshot. An allowed call remains observable but does not produce TG002 or TG003. Disabled codes are not evaluated. Empty rule lists preserve the v0.1 behavior.
