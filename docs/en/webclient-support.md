# WebClient Support Decision

[Documentation home](README.md) | [한국어](../ko/webclient-support.md)

## Status

`WebClient` automatic observation is not supported in Transaction Guard v0.2.

## Why it is not instrumented

Transaction Guard currently follows imperative transactions bound by Spring's `TransactionSynchronizationManager`. A `WebClient` pipeline is lazy and may subscribe, perform I/O, receive a response, or be cancelled on different threads. It may also complete after the imperative transaction has already committed or rolled back.

Capturing the current thread-bound Guard context in a WebClient filter would therefore create ambiguous and unsafe behavior:

- the subscription may start outside the original transaction;
- asynchronous callbacks may mutate a transaction snapshot after completion;
- cancellation and timeout may race with transaction cleanup;
- `THROW` cannot reliably prevent commit when a reactive result finishes later;
- propagating a mutable imperative context through Reactor may leak it into unrelated work.

Transaction Guard does not install a partial WebClient filter because silently observing only some execution shapes would produce misleading results.

## Current alternatives

- Keep remote I/O outside imperative transaction boundaries.
- Use `RestClient` or OpenFeign when a synchronous call must be observed.
- Use Reactor-native transaction and observation tools for fully reactive flows.

## Conditions for future support

Future WebClient support requires a separate Reactor Context-based design with tests for subscription timing, scheduler switches, cancellation, timeout, retry, commit/rollback ordering, and context cleanup. Reactive transaction support must remain distinct from the current imperative transaction implementation.
