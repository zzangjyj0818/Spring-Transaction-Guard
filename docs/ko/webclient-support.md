# WebClient 지원 결정

[문서 홈](README.md) | [English](../en/webclient-support.md)

## 상태

Transaction Guard v0.2에서는 `WebClient` 자동 관측을 지원하지 않습니다.

## 자동 관측하지 않는 이유

현재 Transaction Guard는 Spring `TransactionSynchronizationManager`에 결합된 명령형 트랜잭션을 추적합니다. `WebClient` pipeline은 lazy 방식이며 구독, I/O, 응답, 취소가 서로 다른 스레드에서 실행될 수 있습니다. 명령형 트랜잭션이 이미 commit 또는 rollback된 뒤 완료될 수도 있습니다.

현재 thread-bound Guard Context를 WebClient filter에서 캡처하면 다음과 같은 모호하고 위험한 동작이 생깁니다.

- 원래 트랜잭션 밖에서 구독이 시작될 수 있습니다.
- transaction completion 뒤 비동기 callback이 Snapshot을 변경할 수 있습니다.
- 취소와 timeout이 transaction cleanup과 경합할 수 있습니다.
- reactive 결과가 나중에 완료되면 `THROW`가 commit을 안정적으로 막을 수 없습니다.
- 가변 명령형 Context를 Reactor로 전달하면 관련 없는 작업에 누출될 수 있습니다.

일부 실행 형태만 조용히 관측하면 사용자가 결과를 신뢰하기 어려우므로 불완전한 WebClient filter를 설치하지 않습니다.

## 현재 대안

- 원격 I/O를 명령형 트랜잭션 경계 밖으로 이동하세요.
- 동기 호출 관측이 필요하면 `RestClient` 또는 OpenFeign을 사용하세요.
- 완전한 reactive flow에는 Reactor 기반 transaction 및 observation 도구를 사용하세요.

## 향후 지원 조건

WebClient를 지원하려면 Reactor Context 기반의 별도 설계와 구독 시점, scheduler 전환, 취소, timeout, retry, commit/rollback 순서, context cleanup 테스트가 필요합니다. Reactive transaction 지원은 현재 명령형 transaction 구현과 분리해야 합니다.
