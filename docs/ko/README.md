# Spring Transaction Guard 공식 문서

[English](../en/README.md) | 한국어

Spring Transaction Guard는 실제 활성 DB 트랜잭션의 실행 시간과 트랜잭션 내부의 동기식 외부 HTTP 호출을 관측하는 Spring Boot Starter입니다.

## 문서

1. [시작하기](getting-started.md) — 요구 사항, 설치, 실행
2. [설정 레퍼런스](configuration.md) — 전체 설정과 기본값
3. [탐지 코드와 안전성](detection-and-safety.md) — TG001~TG003, LOG/THROW, 개인정보 처리

## 지원 범위

- Java 21 이상
- Spring Boot 4.1.x / Spring Framework 7.0.x
- `PlatformTransactionManager` 기반 명령형 트랜잭션
- Spring `RestClient`의 동기식 호출

Reactive transaction, `WebClient`, OpenFeign은 0.1.x 지원 범위에 포함되지 않습니다.
