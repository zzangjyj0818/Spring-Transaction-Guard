# 시작하기

[문서 홈](README.md) | [English](../en/getting-started.md)

## 1. 의존성 추가

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.zzangjyj0818:transaction-guard-spring-boot-starter:0.1.0")
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.zzangjyj0818</groupId>
  <artifactId>transaction-guard-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Starter는 별도 활성화 애노테이션 없이 자동 설정됩니다. 기본 설정은 즉시 사용할 수 있으며 필요하면 [설정 레퍼런스](configuration.md)를 참고하세요.

## 2. RestClient 생성

자동 관측을 적용하려면 Spring Boot가 주입하는 `RestClient.Builder`를 사용합니다.

```java
@Bean
RestClient paymentsClient(RestClient.Builder builder) {
    return builder.baseUrl("https://payments.example.com").build();
}
```

직접 만든 `RestClient.create()`와 `RestClient.builder()`는 Boot customizer를 거치지 않아 자동 관측되지 않습니다. 필요하면 `TransactionGuardRestClientConfigurer`로 builder를 명시적으로 구성하세요.

## 3. OpenFeign 사용

OpenFeign이 classpath에 있으면 Transaction Guard가 `TransactionGuardFeignCapability` Bean을 제공합니다. Spring Cloud OpenFeign은 Feign client 설정에 등록된 `Capability` Bean을 사용합니다. 직접 client를 만들 때는 다음처럼 명시적으로 추가하세요.

```java
@Bean
PaymentsClient paymentsClient(TransactionGuardFeignCapability capability) {
    return Feign.builder()
            .addCapability(capability)
            .target(PaymentsClient.class, "https://payments.example.com");
}
```

Capability는 내부 blocking Feign client를 감싸며 원본 Feign 및 transport 예외를 보존합니다. RestClient와 동일한 정제, Ignore, Allow, threshold 규칙이 적용됩니다.

## 4. 예제 실행

```bash
./gradlew :transaction-guard-example:bootRun
```

다른 터미널에서 다음 요청을 실행합니다.

```bash
curl http://localhost:8080/guard/tg001
curl http://localhost:8080/guard/tg002
curl http://localhost:8080/guard/tg003
curl http://localhost:8080/guard/feign
```

- `/guard/tg001`: 장시간 트랜잭션으로 TG001 발생
- `/guard/tg002`: 트랜잭션 내부의 빠른 HTTP 호출로 TG002 발생
- `/guard/tg003`: 트랜잭션 내부의 느린 HTTP 호출로 TG001, TG002, TG003 발생
- `/guard/feign`: OpenFeign 호출로 TG002 발생

예제는 탐지를 보여주기 위해 의도적으로 위험한 패턴을 사용합니다. 실제 서비스에서는 트랜잭션 범위를 줄이고 원격 I/O를 트랜잭션 밖으로 옮기세요.
