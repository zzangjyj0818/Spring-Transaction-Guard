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
| `transaction-guard.violation.mode` | `LOG` | Violation handling mode: `LOG` or `THROW` |
| `transaction-guard.violation.disabled-codes` | `[]` | Disabled codes: `TG001`, `TG002`, or `TG003` |

Durations accept Spring Boot formats such as `200ms`, `2s`, and `1m`. Duration thresholds cannot be negative.

Register a custom `TransactionGuardReporter` bean to replace the default reporter.

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
