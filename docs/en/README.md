# Spring Transaction Guard Documentation

English | [한국어](../ko/README.md)

Spring Transaction Guard is a Spring Boot Starter that observes the duration of actual active database transactions and synchronous external HTTP calls made inside them.

## Guides

1. [Getting Started](getting-started.md) — requirements, installation, and example
2. [Configuration Reference](configuration.md) — all settings and defaults
3. [Detection Codes and Safety](detection-and-safety.md) — TG001–TG003, LOG/THROW, and privacy
4. [WebClient Support Decision](webclient-support.md) — why v0.2 does not auto-instrument WebClient

## Supported environment

- Java 21 or later
- Spring Boot 4.1.x / Spring Framework 7.0.x
- Imperative transactions based on `PlatformTransactionManager`
- Synchronous calls made with Spring `RestClient` or OpenFeign

Reactive transactions and `WebClient` are not supported. See the linked decision for details.
