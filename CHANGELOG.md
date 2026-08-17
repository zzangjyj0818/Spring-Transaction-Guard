# Changelog

이 프로젝트의 주요 변경 사항을 기록합니다.

## [Unreleased]

## [0.4.0] - 2026-08-17

### Added

- Redis operation observation with TG004/TG005 policies and privacy-safe command categories
- Kafka producer completion observation with TG006/TG007 policies
- JDBC query count, failure count, and aggregate duration observation
- Experimental global Query Budget policy (TG008)
- Optional Micrometer metrics for transaction duration, violations, and external HTTP calls
- Fixed-cardinality Redis, Kafka producer, and JDBC Micrometer metrics
- Read-only Transaction Guard Actuator endpoint with bounded, sanitized summaries
- Prometheus-enabled example and bilingual observability guide
- Host/endpoint Ignore 및 Allow glob 규칙
- TG001/TG002/TG003 코드별 비활성화 설정
- Optional OpenFeign Client 관측과 원본 예외 보존
- WebClient 지원 경계와 Reactor Context 기반 향후 조건 문서
- 실행 가능한 Spring Boot 예제와 TG001/TG002/TG003 재현 endpoint
- Starter 기반 end-to-end 통합 테스트
- query, header, body 민감정보 회귀 검증
- 설치, 설정, 아키텍처, 지원 범위 문서
- Maven Central publication metadata와 태그 기반 release workflow
- Redis, Kafka, JDBC 및 Query Budget 한·영 공식 사용 가이드
- Redis/Kafka/JDBC transaction propagation, failure, concurrency, classpath 통합 테스트

### Changed

- RestClient와 OpenFeign이 공통 외부 호출 Snapshot 및 정책 규칙 사용
- LOG 모드에서 Guard 내부 Policy/Reporter 실패를 격리해 비즈니스 흐름 유지
- THROW 모드가 commit 전에 위반 예외를 전달해 트랜잭션을 중단하도록 의미 명확화
- Kafka producer 결과를 반환 future의 transaction 내부 완료 시점에 기록
- JDBC Guard proxy의 중복 wrapping과 `unwrap` 계측 우회 방지

### Security

- Redis key/value/command argument/URI/credential 미수집
- Kafka topic/key/payload/header 미수집
- JDBC SQL/bind parameter/DB URL/credential 미수집

## [0.1.0] - 2026-08-16

### Added

- 실제 Spring transaction lifecycle 기반 Context 생성과 정리
- TG001 장시간 트랜잭션 정책
- TG002 트랜잭션 내부 RestClient 호출 정책
- TG003 느린 외부 HTTP 호출 정책
- REQUIRED, REQUIRES_NEW, NOT_SUPPORTED 전파 지원
- HTTP 성공, 5xx, network failure 관측과 원본 예외 보존
- LOG 및 THROW Reporter
- Spring Boot 4.1 자동 설정, configuration metadata, Starter
- Java 21 / Gradle 9 멀티모듈 빌드와 GitHub Actions 검증
