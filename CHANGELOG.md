# Changelog

이 프로젝트의 주요 변경 사항을 기록합니다.

## [Unreleased]

### Added

- 실행 가능한 Spring Boot 예제와 TG001/TG002/TG003 재현 endpoint
- Starter 기반 end-to-end 통합 테스트
- query, header, body 민감정보 회귀 검증
- 설치, 설정, 아키텍처, 지원 범위 문서

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
