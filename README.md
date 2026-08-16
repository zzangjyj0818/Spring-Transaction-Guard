# Spring Transaction Guard

Spring Transaction Guard detects risky patterns in imperative Spring transactions: long-running transactions and synchronous `RestClient` or OpenFeign calls made while a transaction is active.

Spring Transaction Guard는 명령형 Spring 트랜잭션의 장시간 실행과 트랜잭션 내부의 동기식 `RestClient` 또는 OpenFeign 호출을 감지합니다.

## Documentation / 공식 문서

- [English](docs/en/README.md)
- [한국어](docs/ko/README.md)

## At a glance / 한눈에 보기

| Code | Detection / 탐지 항목 |
|---|---|
| `TG001` | Long transaction / 장시간 트랜잭션 |
| `TG002` | HTTP call inside a transaction / 트랜잭션 내부 HTTP 호출 |
| `TG003` | Slow HTTP call inside a transaction / 트랜잭션 내부의 느린 HTTP 호출 |

The library observes and reports risks. It does not rewrite transaction boundaries, cancel HTTP calls, or replace the transaction manager.

이 라이브러리는 위험을 관측하고 보고하며, 트랜잭션 경계를 변경하거나 HTTP 호출을 취소하거나 트랜잭션 매니저를 대체하지 않습니다.

## Compatibility / 호환성

| Transaction Guard | Java | Spring Boot | Spring Framework |
|---|---:|---:|---:|
| 0.1.x | 21+ | 4.1.x | 7.0.x |
| 0.2.x | 21+ | 4.1.x | 7.0.x |
| 0.3.x | 21+ | 4.1.x | 7.0.x |

## License / 라이선스

[Apache License 2.0](LICENSE)
