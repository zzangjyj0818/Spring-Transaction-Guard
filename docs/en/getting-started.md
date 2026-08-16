# Getting Started

[Documentation home](README.md) | [한국어](../ko/getting-started.md)

## 1. Add the dependency

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

The starter is auto-configured without an enable annotation. The defaults work immediately; see the [Configuration Reference](configuration.md) to customize them.

## 2. Create a RestClient

Use the `RestClient.Builder` injected by Spring Boot for automatic observation.

```java
@Bean
RestClient paymentsClient(RestClient.Builder builder) {
    return builder.baseUrl("https://payments.example.com").build();
}
```

Clients created directly with `RestClient.create()` or `RestClient.builder()` do not pass through Boot's customizer and are not observed automatically. Configure the builder explicitly with `TransactionGuardRestClientConfigurer` when needed.

## 3. Run the example

```bash
./gradlew :transaction-guard-example:bootRun
```

Run these requests in another terminal:

```bash
curl http://localhost:8080/guard/tg001
curl http://localhost:8080/guard/tg002
curl http://localhost:8080/guard/tg003
```

- `/guard/tg001`: triggers TG001 with a long transaction
- `/guard/tg002`: triggers TG002 with a fast HTTP call inside a transaction
- `/guard/tg003`: triggers TG001, TG002, and TG003 with a slow HTTP call inside a transaction

The example intentionally uses risky patterns to demonstrate detection. In production code, keep transactions short and move remote I/O outside the transaction boundary.
