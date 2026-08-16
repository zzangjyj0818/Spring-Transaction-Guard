# Spring Transaction Guard

Spring Transaction Guard는 명령형 Spring 트랜잭션이 오래 유지되거나, 트랜잭션 안에서 동기식 외부 HTTP 호출을 수행하는 위험을 관측하는 Spring Boot Starter입니다. Spring Boot 4.1, Spring Framework 7, Java 21을 기준으로 합니다.

## 감지 항목

| 코드 | 조건 | 기본 심각도 |
|---|---|---|
| `TG001` | 트랜잭션 시간이 `max-duration`을 초과 | WARN |
| `TG002` | 활성 트랜잭션 안에서 `RestClient` 호출 | WARN |
| `TG003` | 트랜잭션 안의 `RestClient` 호출이 `slow-threshold`를 초과 | WARN |

이 도구는 문제를 진단하는 관측 도구입니다. 트랜잭션 경계나 HTTP 호출을 자동으로 변경하지 않습니다.

## 요구 사항

- Java 21 이상
- Spring Boot 4.1.x
- 명령형 Spring Transaction (`PlatformTransactionManager`)
- Spring `RestClient`의 동기식 호출

Reactive transaction과 `WebClient`는 v0.1 지원 범위에 포함되지 않습니다.

## 설치

아직 원격 저장소에 배포되지 않은 개발 버전입니다. 이 저장소를 함께 빌드할 때 애플리케이션에 Starter 하나만 추가합니다.

```kotlin
dependencies {
    implementation(project(":transaction-guard-spring-boot-starter"))
}
```

Starter를 추가하면 별도 `@Enable...` 애노테이션 없이 자동 설정됩니다.

## 설정

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

| 속성 | 기본값 | 설명 |
|---|---:|---|
| `transaction-guard.enabled` | `true` | Guard 전체 활성화 여부 |
| `transaction-guard.transaction.max-duration` | `2s` | TG001 임계값 |
| `transaction-guard.external-call.enabled` | `true` | RestClient 관측 활성화 여부 |
| `transaction-guard.external-call.slow-threshold` | `1s` | TG003 임계값 |
| `transaction-guard.violation.mode` | `LOG` | `LOG` 또는 `THROW` |

애플리케이션에서 `TransactionGuardReporter` Bean을 등록하면 기본 Reporter 대신 사용할 수 있습니다.

## LOG와 THROW

- `LOG`는 기본값이며 구조화된 WARN 로그를 남기고 비즈니스 실행을 의도적으로 중단하지 않습니다.
- `THROW`는 위반 목록을 담은 `TransactionGuardViolationException`을 발생시키는 테스트/CI용 선택 기능입니다. 트랜잭션 완료 콜백에서 실행되므로 사용하는 트랜잭션 매니저가 완료 콜백 예외를 어떻게 전달하는지 반드시 통합 테스트로 확인해야 합니다.

운영 환경에서는 `LOG` 사용을 권장합니다.

## RestClient 사용 시 주의점

Spring Boot가 주입하는 `RestClient.Builder`로 만든 클라이언트에 인터셉터가 자동 등록됩니다.

```java
@Bean
RestClient paymentsClient(RestClient.Builder builder) {
    return builder.baseUrl("https://payments.example.com").build();
}
```

직접 호출한 `RestClient.create()`와 `RestClient.builder()`는 Spring Boot의 customizer를 거치지 않으므로 자동 관측되지 않습니다. 필요한 경우 `TransactionGuardRestClientConfigurer`로 builder를 명시적으로 구성해야 합니다.

## 민감정보 처리

외부 호출 Snapshot에는 HTTP method, host, path, duration, 결과, 예외 타입만 저장합니다. query string, fragment, request/response header, body는 저장하지 않습니다. 따라서 토큰·쿠키·본문 비밀값은 기본 로그에도 포함되지 않습니다. 경로 자체에 개인정보를 넣지 않는 애플리케이션 설계는 여전히 필요합니다.

## 예제 실행

```bash
./gradlew :transaction-guard-example:bootRun
```

다른 터미널에서 다음 요청으로 위반을 재현할 수 있습니다.

```bash
curl http://localhost:8080/guard/tg001
curl http://localhost:8080/guard/tg002
curl http://localhost:8080/guard/tg003
```

- `/guard/tg001`: 200ms 트랜잭션으로 TG001
- `/guard/tg002`: 빠른 로컬 HTTP 호출로 TG002
- `/guard/tg003`: 느린 로컬 HTTP 호출로 TG001, TG002, TG003

예제는 설명을 위해 의도적으로 트랜잭션 안에서 대기와 HTTP 호출을 수행합니다. 실제 서비스에서는 트랜잭션 범위를 줄이고 원격 I/O를 트랜잭션 밖으로 옮기세요.

## 구조

| 모듈 | 역할 |
|---|---|
| `transaction-guard-core` | 모델, 정책, Reporter와 예외 |
| `transaction-guard-spring` | 트랜잭션 lifecycle, AOP, RestClient 관측 |
| `transaction-guard-spring-boot-autoconfigure` | Properties와 조건부 Bean 구성 |
| `transaction-guard-spring-boot-starter` | 사용자용 의존성 진입점 |
| `transaction-guard-example` | 실행 가능한 TG001~TG003 예제와 E2E 검증 |

트랜잭션 Context는 Spring의 transaction resource에 결합됩니다. REQUIRED는 같은 Context를 재사용하고, REQUIRES_NEW는 분리하며, NOT_SUPPORTED로 suspend된 구간의 HTTP 호출은 기록하지 않습니다.

## 빌드와 테스트

```bash
./gradlew clean build
```

테스트에는 실제 H2 transaction manager, 로컬 HTTP 서버, commit/rollback, propagation, 5xx/network failure, 100건 동시 transaction 격리, 개인정보 회귀 검증이 포함됩니다.

자세한 설계와 요구사항은 [docs/SPEC.md](docs/SPEC.md)를 참고하세요.

## 라이선스

Apache License 2.0을 적용합니다. 자세한 조건은 [LICENSE](LICENSE)를 확인하세요.
