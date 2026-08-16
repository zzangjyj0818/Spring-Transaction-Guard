# Spring Transaction Guard

## Product & Technical Specification v0.1.0

**Status:** Implementation Ready
**Primary target:** Spring Boot 4.1.x / Spring Framework 7.0.x
**Recommended JDK:** Java 21 LTS
**Build:** Gradle Kotlin DSL (Gradle 9.x)
**Repository name:** `spring-transaction-guard`

---

## 1. 프로젝트 개요

### 1.1 목적

Spring Transaction Guard는 Spring Boot 애플리케이션에서 **실제 활성 DB 트랜잭션의 실행 시간과 트랜잭션 내부 외부 HTTP I/O를 관찰하여 위험한 트랜잭션 사용 패턴을 탐지하는 개발자용 라이브러리**다.

라이브러리의 목표는 트랜잭션 내부 외부 호출을 무조건 금지하는 것이 아니다. 개발자가 다음과 같은 잠재적 위험을 조기에 인지하고 정책에 따라 로그 또는 테스트 실패로 다룰 수 있도록 하는 것이 목적이다.

- 장시간 유지되는 DB 트랜잭션
- DB 트랜잭션이 열린 상태에서 발생하는 외부 HTTP 호출
- 트랜잭션 내부에서 발생하는 느린 외부 HTTP 호출
- 향후 확장 시 Redis, Kafka, JDBC Query Budget 등 추가 I/O 및 비용 탐지

### 1.2 해결하려는 문제

다음 코드는 기능적으로 정상 동작할 수 있지만 운영 환경에서 장애 전파 위험이 있다.

```java
@Transactional
public Order createOrder(OrderCommand command) {
    Order order = orderRepository.save(Order.create(command));
    paymentClient.pay(order.getId(), order.getPrice());
    return order;
}
```

외부 결제 API가 3초 동안 지연되면 DB 트랜잭션 및 해당 트랜잭션이 점유한 데이터베이스 커넥션도 그 시간 동안 유지될 수 있다. 동시 요청이 증가하면 다음과 같은 장애 흐름으로 이어질 수 있다.

```text
External HTTP latency
        ↓
Long-running transaction
        ↓
DB connection held longer
        ↓
Connection pool pressure
        ↓
API latency increase / timeout
        ↓
Service degradation
```

Transaction Guard는 이 문제를 개발/테스트 단계에서 탐지한다.

### 1.3 비목표(Non-goals)

v0.1.0에서 다음 기능은 구현하지 않는다.

- 트랜잭션을 자동으로 분리하거나 리팩터링
- Transaction Manager 자체 구현/대체
- HTTP 호출을 자동 취소
- Redis/Kafka/File I/O 탐지
- WebClient/OpenFeign 탐지
- JDBC Query Count 또는 N+1 탐지
- 분산 추적 시스템 대체
- 운영 환경에서 강제 Circuit Breaker 역할 수행
- Reactive Transaction 지원

---

## 2. 핵심 설계 원칙

### 2.1 실제 트랜잭션 상태를 기준으로 판단한다

단순히 `@Transactional` 애노테이션 존재 여부만 검사하지 않는다.

실제 트랜잭션 활성 여부는 Spring의 `TransactionSynchronizationManager`를 기준으로 판단한다.

```java
TransactionSynchronizationManager.isActualTransactionActive()
```

이유:

- 클래스 레벨 `@Transactional` 지원
- 메서드 레벨 `@Transactional` 지원
- 외부 라이브러리 또는 Spring Data가 생성한 트랜잭션 대응
- Propagation에 따라 실제 트랜잭션 존재 여부가 달라지는 상황 대응
- 단순 Annotation Pointcut 기반 탐지의 오탐 감소

### 2.2 AOP와 TransactionSynchronization의 역할을 분리한다

AOP는 **호출 위치와 개발자 친화적인 Entry Point 정보 수집**에 사용한다.

TransactionSynchronization은 **실제 트랜잭션 lifecycle과 종료 이벤트 추적**에 사용한다.

```text
Business Method Invocation
        ↓
AOP
 - class
 - method
 - signature
        ↓
Actual Transaction Check
        ↓
TransactionSynchronizationManager
        ↓
Guard Context Register
        ↓
TransactionSynchronization
        ↓
Commit / Rollback / Completion
        ↓
Policy Evaluation
```

### 2.3 Guard는 비즈니스 로직에 침투하지 않는다

사용자는 가능하면 별도의 애노테이션을 추가하지 않아도 된다.

기본 사용 방식:

```gradle
implementation("io.github.<owner>:transaction-guard-spring-boot-starter:0.1.0")
```

```yaml
transaction-guard:
  enabled: true
```

이후 Auto Configuration으로 자동 동작한다.

### 2.4 탐지와 정책 실행을 분리한다

탐지기(Detector)는 사실을 기록한다.

정책(Policy)은 해당 사실을 Violation으로 볼지 판단한다.

Reporter는 결과를 어떻게 처리할지 결정한다.

```text
Instrumentation
      ↓
Observation / Event
      ↓
Policy Engine
      ↓
Violation
      ↓
Reporter
 ├─ Log
 └─ Throw
```

이 구조를 유지하여 향후 Micrometer, Actuator, custom reporter를 추가하기 쉽게 한다.

---

## 3. v0.1.0 기능 범위

### F-001 활성 트랜잭션 탐지

Spring이 관리하는 실제 활성 트랜잭션을 감지한다.

**성공 조건**

- `@Transactional` 메서드 내부에서 활성 상태를 인식한다.
- 트랜잭션이 없는 메서드는 Guard Context를 생성하지 않는다.
- REQUIRED 중첩 호출은 하나의 실제 트랜잭션으로 취급할 수 있어야 한다.
- REQUIRES_NEW는 별도 트랜잭션으로 식별할 수 있는 구조를 갖는다.

### F-002 트랜잭션 실행 시간 측정

트랜잭션 시작 시각과 완료 시각을 기록한다.

시간 측정은 wall clock보다 monotonic clock을 우선한다.

권장:

```java
System.nanoTime()
```

### F-003 장시간 트랜잭션 탐지

설정된 임계값을 초과하면 `TG001`을 발생시킨다.

```text
TG001 LONG_TRANSACTION
```

기본값:

```yaml
transaction-guard:
  transaction:
    max-duration: 2s
```

### F-004 RestClient 외부 HTTP 호출 탐지

활성 DB 트랜잭션 내부에서 Spring `RestClient`가 외부 HTTP 요청을 수행하면 해당 호출을 기록한다.

기록 정보:

- HTTP method
- 대상 URI(민감정보 최소화 고려)
- 호출 시작/종료
- duration
- 성공/실패 여부
- 예외 타입(있는 경우)

### F-005 트랜잭션 내부 HTTP 호출 Violation

트랜잭션 활성 상태에서 HTTP 호출이 발생하면 `TG002`를 생성한다.

```text
TG002 EXTERNAL_HTTP_CALL_IN_TRANSACTION
```

### F-006 느린 HTTP 호출 Violation

트랜잭션 내부 HTTP 호출이 설정된 임계값을 초과하면 `TG003`을 생성한다.

```text
TG003 SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION
```

기본값:

```yaml
transaction-guard:
  external-call:
    enabled: true
    slow-threshold: 1s
```

### F-007 Violation 처리 모드

v0.1.0에서는 다음 두 모드를 지원한다.

```java
public enum ViolationMode {
    LOG,
    THROW
}
```

#### LOG

Violation을 구조화된 로그로 남기고 비즈니스 흐름을 방해하지 않는다.

#### THROW

`TransactionGuardViolationException`을 발생시킨다.

주의: 트랜잭션 종료 이후 THROW 처리 위치와 실제 트랜잭션 결과 사이의 부작용을 명확하게 테스트해야 한다. 테스트 환경에서의 사용을 우선 지원하며, 운영 환경 기본값은 LOG로 한다.

### F-008 Configuration Properties

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

### F-009 Auto Configuration

사용자가 Starter dependency를 추가하면 별도 `@Enable...` 애노테이션 없이 필요한 Bean이 등록되어야 한다.

### F-010 구조화된 Warning 로그

예시:

```text
[TransactionGuard][TG002] External HTTP call detected inside transaction

transaction.id=7c74...
transaction.entryPoint=com.example.order.OrderService#createOrder
transaction.durationMs=3482
transaction.outcome=COMMITTED

externalCall.client=RestClient
externalCall.method=POST
externalCall.host=payments.example.com
externalCall.durationMs=2813

risk=Database transaction remained active while waiting for remote I/O.
```

---

## 4. Violation 정의

| Code | Name | 조건 | 기본 심각도 |
|---|---|---|---|
| TG001 | LONG_TRANSACTION | Transaction duration > `max-duration` | WARN |
| TG002 | EXTERNAL_HTTP_CALL_IN_TRANSACTION | 활성 트랜잭션에서 RestClient 호출 | WARN |
| TG003 | SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION | 활성 트랜잭션의 HTTP 호출 시간이 `slow-threshold` 초과 | WARN |

Violation 모델 예시:

```java
public record TransactionGuardViolation(
        String code,
        ViolationType type,
        ViolationSeverity severity,
        String message,
        TransactionSnapshot transaction,
        Map<String, Object> attributes
) {
}
```

---

## 5. 권장 멀티모듈 구조

```text
spring-transaction-guard/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── LICENSE
│
├── transaction-guard-core/
│   └── src/main/java/...
│       ├── context/
│       ├── model/
│       ├── event/
│       ├── policy/
│       ├── reporter/
│       └── exception/
│
├── transaction-guard-spring/
│   └── src/main/java/...
│       ├── transaction/
│       ├── aop/
│       ├── http/
│       └── support/
│
├── transaction-guard-spring-boot-autoconfigure/
│   └── src/main/java/...
│       ├── TransactionGuardAutoConfiguration.java
│       ├── TransactionGuardProperties.java
│       └── condition/
│
├── transaction-guard-spring-boot-starter/
│   └── build.gradle.kts
│
└── transaction-guard-example/
    └── src/
        ├── main/
        └── test/
```

### 모듈 책임

#### transaction-guard-core

Spring 의존성을 최소화한다.

포함:

- Domain model
- Context abstraction
- Violation
- Policy
- Reporter abstraction
- Clock abstraction(optional)

#### transaction-guard-spring

Spring Framework integration을 담당한다.

포함:

- Transaction 상태 확인
- TransactionSynchronization 등록
- AOP entry point capture
- RestClient instrumentation
- Spring-specific context registry

#### transaction-guard-spring-boot-autoconfigure

Spring Boot 자동 설정을 담당한다.

포함:

- `@AutoConfiguration`
- `@ConfigurationProperties`
- Conditional Bean registration
- Default policy/reporter wiring

#### transaction-guard-spring-boot-starter

의존성 집합만 제공한다. 비즈니스 코드를 넣지 않는다.

#### transaction-guard-example

동작 검증 및 사용자 예제를 제공한다.

---

## 6. 핵심 도메인 모델

### 6.1 TransactionGuardContext

```java
public final class TransactionGuardContext {

    private final String transactionId;
    private final long startedAtNanos;
    private final TransactionEntryPoint entryPoint;
    private final List<ExternalCallObservation> externalCalls;

    private TransactionOutcome outcome;
}
```

권장 필드:

```text
transactionId
startedAtNanos
entryPoint
threadName(optional diagnostics)
externalCalls
outcome
```

Context 내부 collection의 thread-safety에 의존하지 않는다. 기본 imperative transaction은 동일 thread binding을 전제로 하되, context leakage가 없도록 lifecycle을 엄격히 관리한다.

### 6.2 TransactionEntryPoint

```java
public record TransactionEntryPoint(
        String className,
        String methodName,
        String signature
) {
}
```

### 6.3 ExternalCallObservation

```java
public record ExternalCallObservation(
        ExternalClientType clientType,
        String httpMethod,
        String host,
        String path,
        long durationNanos,
        ExternalCallOutcome outcome,
        String exceptionType
) {
}
```

Query parameter와 인증 정보는 기본적으로 기록하지 않는다.

### 6.4 TransactionSnapshot

Transaction이 끝난 뒤 mutable Context를 직접 Reporter에 넘기지 않는다.

완료 시 immutable snapshot으로 변환한다.

```java
public record TransactionSnapshot(
        String transactionId,
        TransactionEntryPoint entryPoint,
        Duration duration,
        TransactionOutcome outcome,
        List<ExternalCallObservation> externalCalls
) {
}
```

---

## 7. Transaction lifecycle 설계

### 7.1 기본 흐름

```text
Service method invocation
        ↓
Transaction already created by Spring proxy
        ↓
Guard Aspect executes
        ↓
isActualTransactionActive() == true
        ↓
Is guard context already registered for this transaction?
        ├─ yes → reuse
        └─ no  → create context
                 register synchronization
        ↓
Business logic
        ↓
RestClient request
        ↓
External call observation appended
        ↓
TransactionSynchronization.afterCompletion(status)
        ↓
Context → Snapshot
        ↓
Policies evaluate
        ↓
Reporter receives violations
        ↓
Context cleanup
```

### 7.2 중복 Context 방지

REQUIRED 전파에서 여러 `@Transactional` 메서드가 같은 실제 트랜잭션에 참여할 수 있다.

```java
@Transactional
public void outer() {
    inner();
}

@Transactional
public void inner() {
}
```

동일 Transaction에 Context를 여러 번 생성하지 않아야 한다.

단순 `ThreadLocal<TransactionGuardContext>` 하나보다 Spring transaction resource binding을 활용하는 방식을 우선 검토한다.

예시 개념:

```java
private static final Object RESOURCE_KEY = TransactionGuardContext.class;

TransactionSynchronizationManager.bindResource(RESOURCE_KEY, context);
```

완료 시 반드시 `unbindResourceIfPossible` 또는 안전한 cleanup을 수행한다.

**구현 전 검증 포인트:** 기존 transaction resource key 충돌, suspend/resume(REQUIRES_NEW), cleanup 순서를 integration test로 검증한다.

### 7.3 REQUIRES_NEW

```java
@Transactional
public void outer() {
    requiresNewService.inner();
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void inner() {
}
```

기대 동작:

```text
Outer Transaction Context A
        ↓ suspend
Inner Transaction Context B
        ↓ complete
Context B report/cleanup
        ↓ resume
Outer Transaction Context A
```

v0.1에서는 최소한 Context 오염/유실이 없어야 한다.

### 7.4 NOT_SUPPORTED

트랜잭션이 suspend된 구간에서 발생한 HTTP 호출은 TG002/TG003 대상으로 보지 않는다.

---

## 8. AOP 설계

AOP의 책임은 다음으로 제한한다.

- Entry point 후보 수집
- 활성 Transaction 확인
- Guard registration trigger

AOP가 트랜잭션 lifecycle 자체를 소유하지 않는다.

권장 component:

```java
@Aspect
final class TransactionGuardAspect {

    @Around("execution(* *(..)) && @within(org.springframework.transaction.annotation.Transactional) || ...")
    Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        // entry point capture + registry ensureRegistered(...)
    }
}
```

실제 Pointcut은 과도하게 광범위하게 잡지 말고 서비스/transaction annotation 기반으로 최적화한다.

**Self-invocation 주의:** Spring proxy를 우회하는 내부 호출에 의존해서 entry point를 판단하지 않는다. 실제 transaction 여부는 항상 `TransactionSynchronizationManager`가 최종 기준이다.

---

## 9. RestClient Instrumentation 설계

### 9.1 v0.1 지원 대상

Spring Framework `RestClient`의 동기식 outbound HTTP 호출만 지원한다.

### 9.2 Interceptor

핵심 구현은 `ClientHttpRequestInterceptor` 기반으로 한다.

```java
public final class TransactionGuardHttpInterceptor
        implements ClientHttpRequestInterceptor {

    private final TransactionGuardRecorder recorder;

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {

        if (!recorder.isTransactionObserved()) {
            return execution.execute(request, body);
        }

        long startedAt = System.nanoTime();

        try {
            ClientHttpResponse response = execution.execute(request, body);
            recorder.recordSuccess(request, System.nanoTime() - startedAt);
            return response;
        } catch (IOException | RuntimeException e) {
            recorder.recordFailure(request, System.nanoTime() - startedAt, e);
            throw e;
        }
    }
}
```

### 9.3 자동 주입 전략

목표는 사용자가 직접 interceptor를 등록하지 않는 것이다.

우선순위:

1. Spring Boot가 제공하는 `RestClient.Builder` customization extension point 사용 가능 여부 검증
2. 전역 RestClient builder customizer 방식
3. 필요한 경우 BeanPostProcessor 검토

**원칙:** internal/private API에 의존하지 않는다.

### 9.4 URI 보안

로그 기본값:

```text
scheme: 미기록 또는 optional
host: 기록
port: optional
path: 기록
query: 기록하지 않음
fragment: 기록하지 않음
Authorization header: 절대 기록하지 않음
body: 절대 기록하지 않음
```

예:

```text
https://payments.example.com/v1/payments?token=SECRET
```

기본 저장:

```text
host=payments.example.com
path=/v1/payments
```

---

## 10. Policy Engine

### 10.1 인터페이스

```java
public interface TransactionGuardPolicy {
    List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot);
}
```

### 10.2 LongTransactionPolicy

```java
final class LongTransactionPolicy implements TransactionGuardPolicy {

    private final Duration threshold;

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        // duration > threshold → TG001
    }
}
```

### 10.3 ExternalHttpCallPolicy

외부 HTTP 호출이 하나 이상 존재하면 각 호출 또는 transaction 단위로 TG002를 생성한다.

v0.1 권장: **호출 단위 Violation**.

### 10.4 SlowExternalHttpCallPolicy

각 external call duration이 threshold를 초과하면 TG003 생성.

---

## 11. Reporter 설계

### 11.1 인터페이스

```java
public interface TransactionGuardReporter {
    void report(List<TransactionGuardViolation> violations);
}
```

### 11.2 LoggingTransactionGuardReporter

기본 Reporter.

요구사항:

- SLF4J 사용
- WARN level
- 한 Violation당 구조화 가능한 key=value 형식
- 민감정보 기록 금지

### 11.3 ThrowingTransactionGuardReporter

테스트/CI용.

```java
public final class TransactionGuardViolationException extends RuntimeException {
    private final List<TransactionGuardViolation> violations;
}
```

다수 violation이 동시에 발생할 수 있으므로 첫 violation만 버리지 않는다.

---

## 12. Spring Boot Auto Configuration

### 12.1 AutoConfiguration

```java
@AutoConfiguration
@EnableConfigurationProperties(TransactionGuardProperties.class)
@ConditionalOnProperty(
        prefix = "transaction-guard",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TransactionGuardAutoConfiguration {
}
```

### 12.2 사용자 override

라이브러리 기본 Bean에는 `@ConditionalOnMissingBean`을 적용하여 사용자가 교체할 수 있게 한다.

예:

```java
@Bean
@ConditionalOnMissingBean(TransactionGuardReporter.class)
TransactionGuardReporter transactionGuardReporter(...) {
    ...
}
```

### 12.3 Configuration Metadata

`spring-boot-configuration-processor`를 적용하여 IDE 자동완성을 제공한다.

---

## 13. Configuration Specification

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

### Properties

| Property | Type | Default | Description |
|---|---|---:|---|
| `transaction-guard.enabled` | boolean | true | Guard 전체 활성화 |
| `transaction-guard.transaction.max-duration` | Duration | 2s | TG001 기준 |
| `transaction-guard.external-call.enabled` | boolean | true | HTTP instrumentation 활성화 |
| `transaction-guard.external-call.slow-threshold` | Duration | 1s | TG003 기준 |
| `transaction-guard.violation.mode` | enum | LOG | LOG / THROW |

---

## 14. 예외 및 실패 정책

### 14.1 Guard 자체 오류는 기본적으로 비즈니스 요청을 깨뜨리지 않는다

LOG 모드에서 Guard 내부 오류가 발생한 경우:

```text
Guard failure → diagnostic log → business flow continues
```

관측 도구 때문에 애플리케이션 자체가 장애 나는 상황을 피한다.

### 14.2 THROW 모드는 명시적 opt-in

THROW는 사용자가 의도적으로 설정한 경우에만 비즈니스 흐름에 영향을 준다.

### 14.3 HTTP 원본 예외 보존

RestClient 호출이 실패한 경우 Guard가 원래 예외를 wrapping하여 타입을 변경하지 않는다.

Observation을 기록한 후 원본 예외를 그대로 throw한다.

---

## 15. 테스트 전략

### 15.1 Unit Test

#### Core model

- Snapshot immutable 여부
- duration 변환 정확성
- external call observation 생성

#### Policy

- duration == threshold → violation 여부 명시
- duration > threshold → TG001
- HTTP call 0개 → TG002 없음
- HTTP call 1개 → TG002
- slow threshold 초과 → TG003
- 여러 call → 예상 violation 개수

권장 경계 조건:

```text
threshold - 1ns
threshold
threshold + 1ns
```

### 15.2 Spring Integration Test

실제 `PlatformTransactionManager`를 사용한다.

권장 테스트 DB: H2 또는 Testcontainers PostgreSQL.

필수 시나리오:

1. 트랜잭션 없는 메서드 → Context 없음
2. `@Transactional` → Context 생성
3. 정상 완료 → COMMITTED
4. RuntimeException rollback → ROLLED_BACK
5. REQUIRED nested → 중복 Context 없음
6. REQUIRES_NEW → Context 분리
7. NOT_SUPPORTED → suspend 구간 HTTP 호출 미탐지
8. 완료 후 Context cleanup 확인

### 15.3 HTTP Integration Test

MockWebServer 또는 WireMock 계열 테스트 서버를 사용한다.

필수:

- transaction + successful RestClient → TG002
- no transaction + RestClient → TG002 없음
- delayed response → TG003
- HTTP 5xx → observation 기록
- network exception → observation 기록 + 원본 exception 유지
- query string의 민감 값이 로그/snapshot에 포함되지 않음

### 15.4 Concurrency Test

가장 중요한 안정성 테스트 중 하나다.

```text
Thread A → Transaction A → HTTP A
Thread B → Transaction B → HTTP B
```

Assertions:

- A snapshot에 B 호출 없음
- B snapshot에 A 호출 없음
- transactionId 서로 다름
- 완료 후 모든 context cleanup

최소 100~1000회 반복 가능한 stress-style integration test를 별도 태그로 둘 수 있다.

### 15.5 Auto Configuration Test

`ApplicationContextRunner` 사용 권장.

검증:

- enabled=false → Guard Bean 없음
- default → Bean 생성
- 사용자 custom Reporter → 기본 Reporter 미생성
- properties binding 정상

---

## 16. Acceptance Criteria v0.1.0

다음 항목을 모두 만족해야 v0.1.0 완료로 본다.

- [ ] Starter dependency 하나로 Auto Configuration이 활성화된다.
- [ ] 실제 Spring transaction 내부에서 Guard Context가 생성된다.
- [ ] 트랜잭션이 없는 요청에서는 Guard가 작동하지 않는다.
- [ ] Transaction duration을 정확하게 측정한다.
- [ ] `max-duration` 초과 시 TG001이 생성된다.
- [ ] RestClient 호출이 transaction 내부에서 발생하면 TG002가 생성된다.
- [ ] 느린 RestClient 호출에서 TG003이 생성된다.
- [ ] RestClient 호출이 transaction 밖에서 발생하면 TG002/TG003이 생성되지 않는다.
- [ ] LOG 모드에서 비즈니스 실행을 방해하지 않는다.
- [ ] THROW 모드에서 `TransactionGuardViolationException`을 발생시킬 수 있다.
- [ ] REQUIRED nested transaction에서 context가 중복 생성되지 않는다.
- [ ] REQUIRES_NEW에서 outer/inner context가 섞이지 않는다.
- [ ] rollback 시 outcome이 올바르게 기록된다.
- [ ] 동시에 수행되는 transaction 간 context가 섞이지 않는다.
- [ ] query parameter, header, body 등 민감 데이터가 기본 로그에 기록되지 않는다.
- [ ] 사용자 custom Reporter를 등록할 수 있다.
- [ ] 모든 Core/Integration test가 통과한다.
- [ ] Example application으로 TG001/TG002/TG003을 재현할 수 있다.

---

## 17. 구현 단계

### Phase 0 - Bootstrap

- Gradle multi-module 생성
- Java 21 toolchain 설정
- Checkstyle/Spotless 중 하나 도입(optional)
- JUnit 5 설정
- GitHub Actions 기본 build/test workflow

### Phase 1 - Core Domain

구현 순서:

1. `ViolationType`
2. `ViolationSeverity`
3. `TransactionEntryPoint`
4. `ExternalCallObservation`
5. `TransactionSnapshot`
6. `TransactionGuardViolation`
7. `TransactionGuardPolicy`
8. 기본 Policy 3종
9. Reporter abstraction
10. Logging/Throwing Reporter

### Phase 2 - Transaction Observation

1. Transaction active detector
2. Context registry
3. Synchronization registration
4. Completion callback
5. Snapshot creation
6. Policy evaluation
7. cleanup

이 Phase 완료 시 HTTP 탐지 없이 TG001이 동작해야 한다.

### Phase 3 - RestClient Observation

1. `TransactionGuardHttpInterceptor`
2. RestClient builder 자동 customization
3. successful call observation
4. failed call observation
5. URI sanitization
6. TG002/TG003 integration

### Phase 4 - Boot Auto Configuration

1. Properties
2. AutoConfiguration
3. Conditional beans
4. configuration metadata
5. starter module

### Phase 5 - Integration / Quality

1. propagation tests
2. concurrency tests
3. failure-path tests
4. example app
5. README
6. CHANGELOG
7. LICENSE

---

## 18. Codex 구현 규칙

아래 규칙은 구현 중 반드시 유지한다.

### Architecture

1. `core` 모듈이 Spring Boot에 의존하지 않도록 한다.
2. `spring` 모듈은 Spring Framework에 의존할 수 있다.
3. `autoconfigure` 모듈에서만 Spring Boot Auto Configuration을 구현한다.
4. `starter` 모듈에는 가능한 한 Java source를 넣지 않는다.
5. domain model과 Spring infrastructure를 같은 package에 섞지 않는다.

### Correctness

6. `@Transactional` annotation 존재 여부만으로 transaction 활성 여부를 판단하지 않는다.
7. 실제 판단은 `TransactionSynchronizationManager.isActualTransactionActive()`를 사용한다.
8. Context를 생성했다면 모든 종료 경로에서 반드시 cleanup한다.
9. REQUIRED/REQUIRES_NEW/NOT_SUPPORTED 테스트 없이 lifecycle 구현을 완료 처리하지 않는다.
10. RestClient 원본 exception을 변경하지 않는다.

### Safety

11. HTTP body를 수집하지 않는다.
12. Authorization/Cookie header를 수집하지 않는다.
13. URI query parameter를 기본적으로 기록하지 않는다.
14. Guard 내부 오류는 LOG 모드에서 business flow를 깨뜨리지 않는다.

### Maintainability

15. 시간 측정 코드가 직접 여러 클래스에 흩어지지 않도록 한다.
16. Policy와 Reporter를 interface로 분리한다.
17. public API에는 Javadoc을 작성한다.
18. package-private로 가능한 구현체는 public으로 노출하지 않는다.
19. 설정 값은 magic number로 사용하지 않는다.
20. 하나의 클래스가 transaction detection + policy + logging을 모두 수행하지 않는다.

### Testing

21. 신규 기능은 관련 test와 함께 구현한다.
22. bug fix 시 regression test를 먼저 또는 동시에 추가한다.
23. concurrency 관련 코드는 단일 thread test만으로 완료 처리하지 않는다.
24. Spring internals에 의존하는 코드는 실제 ApplicationContext integration test를 작성한다.

---

## 19. Codex 첫 작업 프롬프트

아래 내용을 Codex에 그대로 전달하여 시작할 수 있다.

```text
You are implementing an open-source Spring Boot library named `spring-transaction-guard`.

Read `docs/SPEC.md` completely before changing any code. Treat the specification as the source of truth.

Goal for the first milestone:
Build the project skeleton and implement Phase 1 (Core Domain) only. Do not implement Spring transaction instrumentation or RestClient support yet.

Requirements:
- Java 21
- Gradle Kotlin DSL multi-module project
- Modules:
  - transaction-guard-core
  - transaction-guard-spring
  - transaction-guard-spring-boot-autoconfigure
  - transaction-guard-spring-boot-starter
  - transaction-guard-example
- Keep transaction-guard-core free from Spring Boot dependencies.
- Implement the domain models, policy interfaces, TG001/TG002/TG003 policies, reporter abstraction, logging reporter, and throwing reporter described in SPEC.md.
- Add comprehensive JUnit 5 tests for all policy boundary cases.
- Prefer immutable records/value objects where appropriate.
- Do not invent features outside the specification.
- After implementation, run the full Gradle test suite and report:
  1. files created/modified,
  2. architecture decisions,
  3. test results,
  4. unresolved questions or risks.
```

Phase 1 완료 후 다음 프롬프트:

```text
Continue the `spring-transaction-guard` implementation using `docs/SPEC.md` as the source of truth.

Implement Phase 2: Transaction Observation.

Focus only on imperative Spring transactions.
Use TransactionSynchronizationManager as the authoritative source of actual transaction state.
Use TransactionSynchronization for lifecycle completion.
Do not use @Transactional annotation presence as the source of truth.

Required tests:
- no transaction
- normal commit
- rollback
- REQUIRED nesting
- REQUIRES_NEW suspend/resume
- NOT_SUPPORTED suspend/resume
- context cleanup
- concurrent independent transactions

Do not implement RestClient support until all Phase 2 tests pass.
Run the full test suite and summarize architecture, changed files, test results, and remaining risks.
```

Phase 2 완료 후:

```text
Continue from `docs/SPEC.md` and implement Phase 3: RestClient Observation.

Requirements:
- instrument Spring RestClient outbound calls via supported public extension points
- automatically apply instrumentation to Boot-managed RestClient.Builder where possible
- detect outbound calls only while a Guard-observed transaction is active
- preserve original HTTP exceptions
- never record request/response bodies, Authorization/Cookie headers, or URI query parameters
- implement TG002 and TG003 end-to-end
- add HTTP integration tests including success, failure, slow response, no-transaction, and sensitive URI sanitization

Do not add WebClient or OpenFeign support.
Run the full test suite and report results and design tradeoffs.
```

---

## 20. 향후 로드맵

### v0.2

- WebClient 지원 검토
- OpenFeign 지원
- endpoint/host ignore rules
- allow rules
- 특정 violation disable

### v0.3

- Micrometer metrics
- Actuator endpoint
- Prometheus integration example

예상 metric:

```text
transaction.guard.transaction.duration
transaction.guard.violation.total
transaction.guard.external.http.duration
transaction.guard.external.http.total
```

### v0.4

- Redis operation detection
- Kafka producer call detection
- JDBC query count observation
- Query Budget 실험

### v1.0

- API 안정화
- Spring Boot 4.x minor-version compatibility strategy
- Maven Central publication
- semantic versioning
- contribution guide
- public extension API 안정화

---

## 21. 기술 리스크

### R-001 Transaction boundary 정확성

가장 큰 위험이다. AOP invocation과 실제 transaction start/end timing이 완전히 동일하지 않을 수 있다.

대응:

- TransactionSynchronizationManager를 최종 기준으로 사용
- integration test 우선
- propagation별 동작 명시

### R-002 REQUIRES_NEW suspend/resume

ThreadLocal만 단순 사용하면 outer context가 inner context에 오염될 가능성이 있다.

대응:

- Spring transaction resource binding 또는 stack-aware registry 검토
- suspend/resume integration test 필수

### R-003 THROW timing

Transaction completion callback 시점에서 예외를 던지는 것이 application semantics에 미치는 영향이 복잡할 수 있다.

대응:

- LOG를 기본값으로 유지
- THROW를 test/CI 중심 기능으로 명시
- 실제 commit/rollback 결과와 exception propagation 테스트

### R-004 RestClient customization coverage

사용자가 `RestClient.create()`를 직접 호출하여 Spring Bean lifecycle 밖에서 생성한 client는 자동 instrumentation 대상이 아닐 수 있다.

대응:

- v0.1 문서에 지원 범위를 명확히 기재
- Boot-managed `RestClient.Builder` 우선 지원
- 향후 optional manual customizer API 제공 검토

### R-005 오탐

트랜잭션 내부 HTTP 호출이 항상 잘못된 것은 아니다.

대응:

- Warning/Violation 용어 사용
- 기본 LOG
- 향후 ignore/allow 정책 제공

---

## 22. Definition of Done

기능 하나를 완료했다고 판단하려면 다음을 만족해야 한다.

1. Specification의 해당 Acceptance Criteria 충족
2. Unit 또는 Integration test 존재
3. 실패 경로 테스트 존재
4. public API Javadoc 존재
5. 민감정보 유출 여부 검토
6. `./gradlew test` 성공
7. Example 또는 README 사용법과 실제 동작이 일치
8. 불필요한 public API가 노출되지 않음

---

## 23. 참고 기술 문서

구현 시 Spring Framework 내부 구현을 추측하지 말고 공식 API/Reference 문서를 우선 확인한다.

- Spring Boot 4.1 System Requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Transaction Resource Synchronization: https://docs.spring.io/spring-framework/reference/data-access/transaction/tx-resource-synchronization.html
- TransactionSynchronizationManager Javadoc: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html
- TransactionSynchronization Javadoc: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html
- Spring REST Clients Reference: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html

---

## 24. 최종 구현 방향 요약

```text
Spring Transaction Guard

Application
    ↓
Spring Transaction
    ↓
Transaction Guard Registration
    ↓
TransactionGuardContext
    ├── start time
    ├── entry point
    └── external call observations
            ↑
      RestClient Interceptor
    ↓
Transaction Completion
    ↓
Immutable TransactionSnapshot
    ↓
Policy Engine
    ├── TG001 Long Transaction
    ├── TG002 HTTP Inside Transaction
    └── TG003 Slow HTTP Inside Transaction
    ↓
Reporter
    ├── LOG (default)
    └── THROW (test/CI)
```

v0.1.0의 핵심 성공 기준은 **“Spring이 실제 관리하는 트랜잭션 lifecycle을 정확히 추적하면서 RestClient 외부 I/O를 안전하게 관찰하고, 애플리케이션 코드 변경을 최소화한 Starter 형태로 제공하는 것”**이다.
