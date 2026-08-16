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
  violation:
    mode: log
```

| Property | Default | Description |
|---|---:|---|
| `transaction-guard.enabled` | `true` | Enables Transaction Guard globally |
| `transaction-guard.transaction.max-duration` | `2s` | Transaction duration threshold for TG001 |
| `transaction-guard.external-call.enabled` | `true` | Enables `RestClient` observation |
| `transaction-guard.external-call.slow-threshold` | `1s` | HTTP call duration threshold for TG003 |
| `transaction-guard.violation.mode` | `LOG` | Violation handling mode: `LOG` or `THROW` |

Durations accept Spring Boot formats such as `200ms`, `2s`, and `1m`. Duration thresholds cannot be negative.

Register a custom `TransactionGuardReporter` bean to replace the default reporter.
