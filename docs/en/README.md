# Spring Transaction Guard Documentation

English | [한국어](../ko/README.md)

Spring Transaction Guard is a Spring Boot Starter that observes actual database-transaction duration, HTTP/Redis/Kafka I/O, and JDBC query count.

## Guides

1. [Getting Started](getting-started.md) — requirements, installation, and example
2. [Configuration Reference](configuration.md) — all settings and defaults
3. [Using Redis, Kafka, and JDBC Observation](io-observation.md) — setup, Query Budget, and supported boundaries
4. [Detection Codes and Safety](detection-and-safety.md) — TG001–TG008, LOG/THROW, and privacy
5. [Observability and Prometheus](observability.md) — Micrometer metrics, the Actuator endpoint, and Prometheus
6. [WebClient Support Decision](webclient-support.md) — why WebClient is not auto-instrumented

## Supported environment

- Java 21 or later
- Spring Boot 4.1.x / Spring Framework 7.0.x
- Imperative transactions based on `PlatformTransactionManager`
- Synchronous calls made with Spring `RestClient` or OpenFeign
- Imperative Spring Data Redis Operations
- Spring Kafka `KafkaTemplate` producer completion
- JDBC Statement execution through a Spring-managed `DataSource`

Reactive transactions, `WebClient`, Reactive Redis, Kafka consumers, and vendor-specific JDBC execution APIs are not supported. See each guide for the exact boundary.
