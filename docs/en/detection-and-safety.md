# Detection Codes and Safety

[Documentation home](README.md) | [한국어](../ko/detection-and-safety.md)

## Detection codes

| Code | Name | Condition |
|---|---|---|
| `TG001` | `LONG_TRANSACTION` | Transaction duration exceeds `max-duration` |
| `TG002` | `EXTERNAL_HTTP_CALL_IN_TRANSACTION` | A `RestClient` call occurs inside an active transaction |
| `TG003` | `SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION` | A `RestClient` call inside a transaction exceeds `slow-threshold` |

TG002 and TG003 apply to supported blocking clients: Spring `RestClient` and OpenFeign. Allowed destinations remain in the snapshot but are excluded from these two policies. Ignored destinations are not recorded at all.

Nested REQUIRED calls reuse the same actual transaction context, while REQUIRES_NEW uses a separate context. HTTP calls made while a transaction is suspended by NOT_SUPPORTED are not recorded.

## LOG and THROW

- `LOG` is the default. It writes structured WARN logs after actual transaction completion. If an internal policy or reporter fails, the guard emits a diagnostic ERROR log and preserves the business outcome.
- `THROW` evaluates policies immediately before commit and raises `TransactionGuardViolationException`. The exception prevents the commit and leads to rollback, so use this mode to enforce safe patterns in tests and CI.

`LOG` is recommended for production environments.

## Privacy and sensitive data

External-call snapshots store only the HTTP method, host, path, duration, outcome, and exception type. Query strings, fragments, request/response headers, and bodies are not stored, so tokens, cookies, and body secrets are excluded from default logs.

A URL path can itself contain identifiers or secrets and may be recorded. Applications should therefore avoid placing sensitive information in paths.
