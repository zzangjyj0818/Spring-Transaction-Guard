# Detection Codes and Safety

[Documentation home](README.md) | [한국어](../ko/detection-and-safety.md)

## Detection codes

| Code | Name | Condition |
|---|---|---|
| `TG001` | `LONG_TRANSACTION` | Transaction duration exceeds `max-duration` |
| `TG002` | `EXTERNAL_HTTP_CALL_IN_TRANSACTION` | A `RestClient` call occurs inside an active transaction |
| `TG003` | `SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION` | A `RestClient` call inside a transaction exceeds `slow-threshold` |
| `TG004` | `REDIS_OPERATION_IN_TRANSACTION` | An imperative Redis operation occurs inside an active transaction |
| `TG005` | `SLOW_REDIS_OPERATION_IN_TRANSACTION` | A Redis operation exceeds `redis.slow-threshold` |
| `TG006` | `KAFKA_PRODUCER_CALL_IN_TRANSACTION` | A Kafka producer send completes inside an active transaction |
| `TG007` | `SLOW_KAFKA_PRODUCER_CALL_IN_TRANSACTION` | Kafka send completion exceeds `kafka.slow-threshold` |
| `TG008` | `QUERY_BUDGET_EXCEEDED` | JDBC query count exceeds the experimental budget |

TG002 and TG003 apply to supported blocking clients: Spring `RestClient` and OpenFeign. Allowed destinations remain in the snapshot but are excluded from these two policies. Ignored destinations are not recorded at all.

Nested REQUIRED calls reuse the same actual transaction context, while REQUIRES_NEW uses a separate context. HTTP calls made while a transaction is suspended by NOT_SUPPORTED are not recorded.

The same propagation rules apply to Redis, Kafka, and JDBC. Kafka records success or failure only when the future returned by `send` completes while the Guard-observed transaction is still active. An asynchronous result completed after transaction completion is not added retroactively to an already completed snapshot.

## LOG and THROW

- `LOG` is the default. It writes structured WARN logs after actual transaction completion. If an internal policy or reporter fails, the guard emits a diagnostic ERROR log and preserves the business outcome.
- `THROW` evaluates policies immediately before commit and raises `TransactionGuardViolationException`. The exception prevents the commit and leads to rollback, so use this mode to enforce safe patterns in tests and CI.

`LOG` is recommended for production environments.

## Privacy and sensitive data

External-call snapshots store only the HTTP method, host, path, duration, outcome, and exception type. Query strings, fragments, request/response headers, and bodies are not stored, so tokens, cookies, and body secrets are excluded from default logs.

Redis observations contain only client type, command category, duration, outcome, and exception type; keys, values, command arguments, and connection URIs are excluded. Kafka observations exclude topics, keys, payloads, and headers. JDBC observation aggregates only query count, failure count, and total duration; SQL, bind parameters, database URLs, and credentials are excluded.

A URL path can itself contain identifiers or secrets and may be recorded. Applications should therefore avoid placing sensitive information in paths.
