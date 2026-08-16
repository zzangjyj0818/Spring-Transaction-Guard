# 탐지 코드와 안전성

[문서 홈](README.md) | [English](../en/detection-and-safety.md)

## 탐지 코드

| 코드 | 이름 | 발생 조건 |
|---|---|---|
| `TG001` | `LONG_TRANSACTION` | 트랜잭션 시간이 `max-duration`을 초과 |
| `TG002` | `EXTERNAL_HTTP_CALL_IN_TRANSACTION` | 활성 트랜잭션 안에서 `RestClient` 호출 |
| `TG003` | `SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION` | 트랜잭션 안의 `RestClient` 호출이 `slow-threshold`를 초과 |
| `TG004` | `REDIS_OPERATION_IN_TRANSACTION` | 활성 트랜잭션 안에서 명령형 Redis 작업 실행 |
| `TG005` | `SLOW_REDIS_OPERATION_IN_TRANSACTION` | Redis 작업이 `redis.slow-threshold`를 초과 |
| `TG006` | `KAFKA_PRODUCER_CALL_IN_TRANSACTION` | 활성 트랜잭션 안에서 Kafka producer send 완료 |
| `TG007` | `SLOW_KAFKA_PRODUCER_CALL_IN_TRANSACTION` | Kafka send 완료 시간이 `kafka.slow-threshold`를 초과 |
| `TG008` | `QUERY_BUDGET_EXCEEDED` | JDBC query 수가 설정한 실험적 예산을 초과 |

TG002와 TG003은 지원하는 blocking client인 Spring `RestClient`와 OpenFeign에 적용됩니다. Allow 대상은 Snapshot에 남지만 두 정책에서는 제외되고, Ignore 대상은 기록하지 않습니다.

REQUIRED 중첩 호출은 같은 실제 트랜잭션 Context를 재사용하고 REQUIRES_NEW는 별도 Context를 사용합니다. NOT_SUPPORTED로 트랜잭션이 중단된 구간의 HTTP 호출은 기록하지 않습니다.

같은 전파 규칙이 Redis, Kafka, JDBC에도 적용됩니다. Kafka는 `send`가 반환한 future가 Guard가 관측하는 트랜잭션 안에서 완료된 경우에만 성공 또는 실패를 기록합니다. 트랜잭션 종료 이후에 완료된 비동기 결과는 이미 완성된 Snapshot에 소급해 추가하지 않습니다.

## LOG와 THROW

- `LOG`는 기본값입니다. 실제 transaction completion 이후 구조화된 WARN 로그를 기록합니다. 내부 Policy 또는 Reporter가 실패하면 진단 ERROR 로그를 남기되 비즈니스 실행 결과는 유지합니다.
- `THROW`는 commit 직전에 정책을 평가하고 `TransactionGuardViolationException`을 발생시킵니다. 이 예외는 commit을 막고 rollback을 유도하므로 테스트와 CI에서 위험 패턴을 강제할 때 사용하세요.

운영 환경에는 `LOG`를 권장합니다.

## 개인정보와 민감정보

외부 호출 Snapshot에는 HTTP method, host, path, duration, 결과, 예외 타입만 저장합니다. Query string, fragment, request/response header, body는 저장하지 않습니다. 따라서 토큰, 쿠키, 본문 비밀값은 기본 로그에 포함되지 않습니다.

Redis 관측에는 client type, 명령 category, duration, 결과와 예외 타입만 저장하며 key, value, 명령 인자와 connection URI는 저장하지 않습니다. Kafka 관측에는 topic, key, payload, header를 저장하지 않습니다. JDBC 관측은 query 수, 실패 수와 총 duration만 집계하며 SQL, bind parameter, DB URL과 credential을 저장하지 않습니다.

URL path 자체에 사용자 식별자나 비밀값이 있으면 path가 기록될 수 있으므로 애플리케이션에서도 경로에 민감정보를 넣지 않아야 합니다.
