# Spring Transaction Guard 공식 문서

[English](../en/README.md) | 한국어

Spring Transaction Guard는 실제 활성 DB 트랜잭션의 실행 시간, HTTP/Redis/Kafka I/O와 JDBC query 수를 관측하는 Spring Boot Starter입니다.

## 문서

1. [시작하기](getting-started.md) — 요구 사항, 설치, 실행
2. [설정 레퍼런스](configuration.md) — 전체 설정과 기본값
3. [Redis, Kafka, JDBC 관측 사용법](io-observation.md) — 적용 방법, Query Budget, 지원 경계
4. [탐지 코드와 안전성](detection-and-safety.md) — TG001~TG008, LOG/THROW, 개인정보 처리
5. [관측성과 Prometheus](observability.md) — Micrometer 메트릭, Actuator endpoint, Prometheus 연동
6. [WebClient 지원 결정](webclient-support.md) — 자동 관측하지 않는 이유

## 지원 범위

- Java 21 이상
- Spring Boot 4.1.x / Spring Framework 7.0.x
- `PlatformTransactionManager` 기반 명령형 트랜잭션
- Spring `RestClient` 또는 OpenFeign의 동기식 호출
- 명령형 Spring Data Redis Operations
- Spring Kafka `KafkaTemplate` producer 완료
- Spring-managed `DataSource`를 통한 JDBC Statement 실행

Reactive transaction, `WebClient`, Reactive Redis, Kafka consumer와 vendor 전용 JDBC 실행 API는 지원하지 않습니다. 자세한 내용은 각 가이드의 지원 경계를 참고하세요.
