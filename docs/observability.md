# Observability implementation

Started 2026-11-03 on main. This is the first tracing increment of the
[diagnostics and backup plan](telemetry-diagnostics-backup-plan.md), not the
completed desktop diagnostics feature.

## Shared pipeline

`core:telemetry` owns the pinned official OpenTelemetry Kotlin SDK **0.9.0**.
Business code receives `Telemetry`, with `Telemetry.None` as its default; it
does not depend on SDK APIs. Android, JVM, and iOS compile the same sources.
The Android library target runs the common tests without requiring an Android UI.

`Telemetry.trace` propagates context in a coroutine context element. Structured
child coroutines and dispatcher switches inherit it. Jobs launched on an
unrelated scope must capture `currentContext()` and pass `parent` explicitly;
the reflection and spoken-question jobs do this. Native bridges can pass a W3C
`traceparent` via `TraceContext` and `withParent`. The Rust and Swift internals
have not yet been instrumented to consume that carrier.

Span timestamps use a UTC baseline plus monotonic elapsed time, so clock changes
during an operation do not change its duration. Queue writes never suspend the
caller. One background worker batches up to 64 records or 250 ms, with a
512-record waiting queue. Overflow increments dropped-record counts.

The iOS composition root enables local collection, assigns a persistent random
installation ID in preferences, adds bundle/version/build/platform metadata,
and emits lifecycle events. Telemetry initialization failure falls back to the
no-op implementation. No network exporter or collector is configured.

## Instrumented boundaries

| Flow | Recorded boundaries |
|---|---|
| Task | Run, phone lock wait, memory preparation, first successful phone action, outcome, cancellation, cleanup |
| Connection | Session acquisition, session lock wait, connect, disconnect, stop session |
| iOS connection | Pairing load, built-in VPN start/stop, CoreDevice session open, OCR warmup |
| Agent | Run, Brain brief/decision, each screen look |
| Phone | Each operation by wire name; stream pause/resume |
| iOS perception | PNG capture, JPEG conversion, OCR; reused captures remain reused |
| Model | Each logical chat call, provider/model/effort, input/output/cache token counts, failures |
| Account | Claude/ChatGPT access-token acquisition, including refresh when needed; no credential values |
| Voice | Speaking, provider synthesis attempts, hearing, recording, transcription attempts, spoken question turn |
| Memory | Background reflection correlated to the originating task |

First-action duration starts when the runner is invoked, before waiting for the
phone lock. It ends when the first non-read phone operation succeeds. It excludes
time already spent waiting in the persisted task queue and does not measure
visible animation completion. It is an OTel event/correlated log with
`mode.duration_ms`, not a Kotlin metric instrument.

## Files and privacy

On iOS, files live under **Documents/logs/telemetry/v1/**. Each `.traces.pb` is a
complete OTLP `ExportTraceServiceRequest`; each `.logs.pb` is a complete OTLP
`ExportLogsServiceRequest`. They use the official SDK's protobuf encoder, not
a private trace schema. The directory can be copied with the app's existing
logs; desktop fetching and report export are still pending.

Retention removes the oldest records beyond **seven days, 50 MiB, or 4,096
files**, whichever limit is reached first. The shared `FileStore` adapter must
provide atomic writes. The iOS adapter already does. Every write is checked
before being counted as persisted. There is no index that must be rebuilt after
a crash. A crash can lose queued records and leave a task trace incomplete.

`health.json` reports the current process's dropped records, failed batches, and
written records. Counts are also available in `LocalTelemetry.health`. A flush
waits for records already queued; it does not finish active spans. Collection
failures do not replace task results or suppress task cancellation.

Only enumerated operation/event names and allowlisted fields enter this
pipeline. Values are validated; numeric measurements remain numeric in OTLP.
No prompt/response bodies, tool arguments, typed text, OCR contents, images,
credentials, pairing contents, raw exception messages, or stacks are exported.
The older debug run logs and screenshots still contain their existing detailed
data; copying the entire logs folder is not a metadata-only diagnostics export.

## Verification

The common tests run on JVM, the iOS simulator, and the Android library host
target. They cover parallel traces and dispatcher changes, remote/invalid W3C
parents, real OTLP encode/decode, redaction, cancellation, lock ownership,
bounded queue overflow, broken storage, no-op behavior, and retention/reopening.

```sh
./gradlew :core:telemetry:jvmTest \
  :core:telemetry:iosSimulatorArm64Test \
  :core:telemetry:testAndroidHostTest
```

The existing agent, ops, model, connection, voice/settings, tasks, and iOS backend
tests are also run. The iOS device graph compiles and the simulator framework
links. A physical phone has not yet validated these changes, and device overhead
has not yet been measured.

## Remaining delivery

- Instrument persisted queue/admission waits, permission changes, authentication
  refresh, individual HTTP retries and actual first response/token arrival.
- Propagate W3C context into native Rust/Swift work; split developer-service,
  developer-image, stream startup/first frame/stall/reconnect, audio-session,
  interruption, and teaching/persistence boundaries.
- Instrument desktop operations using OTel Rust, implement shared OTLP decoding,
  deterministic findings/metric aggregation, fetching, waterfall, and report ZIP.
- Implement Android device adapters and test transport on a real Android device.
- Benchmark physical-device overhead and validate crash/incomplete-trace handling.
- Implement encrypted settings/account backup and restore after diagnostics.

Sources: [official Kotlin setup](https://opentelemetry.io/docs/languages/kotlin/getting-started/),
[pinned SDK source](https://github.com/open-telemetry/opentelemetry-kotlin/tree/v0.9.0),
[Android KMP test configuration](https://developer.android.com/kotlin/multiplatform/plugin#configure-tests).
