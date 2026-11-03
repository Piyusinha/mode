# Cross-Platform OpenTelemetry, Diagnostics, and Settings Backup

Agreed implementation plan, 2026-11-03. Implementation has started; see
[observability.md](observability.md) for delivered tracing coverage and remaining
work. The full diagnostics and backup feature is not complete. This replaces the
earlier platform-specific tracing proposal.

## 1. Architecture and reuse

Implement shared infrastructure for **iOS, Android, macOS, Windows, and Linux**.

- Add `core:telemetry` in Kotlin Multiplatform. Shared agent, networking, tasks,
  memory, voice, and connection code receives an injected telemetry interface.
- Use the official **OpenTelemetry Kotlin Multiplatform SDK** for traces and
  correlated logs. It supports Android, JVM, and iOS; its API is experimental,
  so pin its version and isolate SDK-specific code inside this module.
  [Official support and stability](https://opentelemetry.io/docs/languages/kotlin/getting-started/)
- Use **OpenTelemetry Rust** for desktop/native instrumentation and metric
  aggregation. [Rust implementation](https://github.com/open-telemetry/opentelemetry-rust)
- Use **OTLP** for portable telemetry records and optional collector export.
  Do not introduce a proprietary trace format.
  [OTLP specification](https://opentelemetry.io/docs/specs/otlp/)
- Keep collection local by default. Fetching, diagnosis, and export require
  neither an AI service nor a hosted telemetry backend.

Android reuses instrumentation, operation names, redaction policy, diagnostics
rules, archive format, and backup logic. It needs adapters for Android-specific
screen capture, connection services, lifecycle, secure storage, and device
transport, not another implementation of the feature.

**Metrics limitation:** OpenTelemetry Kotlin currently exposes tracing and
logging, but not its Metrics API. For v1, record measurements through shared
OTel spans/events; derive mobile timing distributions and counters in the
shared Rust diagnostics processor, emitting standard OTel metrics. Desktop/native
code can use Rust metric instruments directly. Do not claim unsupported Kotlin
metrics are available.
[Official instrumentation documentation](https://opentelemetry.io/docs/languages/kotlin/instrumentation/)

## 2. Instrumentation and telemetry contract

Define shared interfaces for scoped operations, structured events, measurements,
context propagation, and export. Business modules depend on these interfaces,
with a no-op implementation for tests and disabled telemetry.

Instrument:

- App initialization, task admission, queue/lock waits, memory preparation,
  and time to first action.
- VPN permissions, configuration, startup, shutdown, failures, and reconnects.
- Pairing, developer-service discovery, developer-image mounting, and session
  teardown.
- Screen-stream startup, first frame, capture, image conversion, OCR, stalls,
  and recovery.
- Credential refresh, Brain planning, AI dispatch, first response, completion,
  retries, and token/cache usage.
- App launch, taps, swipes, typing, settling, and verification.
- Recording, transcription, speech, audio-session transitions, and interruptions.
- Reflection, persistence, teaching, cancellation, and cleanup.
- Desktop discovery, trust, authentication, signing, installation, repair,
  fetching, and backup.

Use explicit coroutine-safe context propagation. Pass W3C trace context across
Kotlin/Swift/Rust bridges and desktop operations; correlate separate processes
with parent context or span links rather than timestamps alone.

Use standard semantic conventions where applicable and versioned `mode.*`
attributes elsewhere. Record monotonic durations and UTC timestamps. Model
retries separately and calculate critical-path time without adding overlapping
spans together.

Keep high-cardinality task IDs in traces, not metric labels. Never record
credentials, pairing contents, prompts, typed text, screenshot contents, or
OCR text in default telemetry.

Buffer asynchronously, rotate after seven days or 50 MB, and expose dropped-record
counts. Telemetry failure must never block phone control.

## 3. Desktop diagnostics and repair

Add a Diagnostics page with:

- **Fetch diagnostics**, installation selection, progress, and cancellation.
- A task waterfall showing connection, planning, screen capture, inference,
  and first-action timings.
- **Analyze locally** and **Export report**.
- ZIP export containing OTLP records, resource/build metadata, and a readable
  findings report.

Define a common `DeviceDiagnosticsTransport` interface. Implement iOS fetching
through House Arrest/AFC and an Android adapter through an explicitly authorized
ADB connection. Also support importing an exported archive, so analysis is
independent of transport.

Implement one deterministic Rust rule engine for every platform. Separate
portable rules from capability-specific rules: Android must not receive iOS
pairing or developer-image advice.

Initial rules identify connection failures, missing prerequisites, slow startup
stages, retries, queue contention, stream stalls, incomplete operations, and
differing model/effort settings between installations.

Each finding contains evidence, severity, likely explanation, recommended action,
and limitations. Incomplete evidence produces "Insufficient data."

Offer user-triggered repair buttons where supported. Never silently reinstall,
revoke certificates, change models, or alter access permissions. Metadata-only
export is the default; sensitive legacy logs/screenshots require explicit
selection.

## 4. Portable encrypted settings backup

Add Backup and Restore to the desktop app, using a shared versioned archive
contract.

Include all preferences and AI-provider credential bundles. Exclude chats, memory,
teaching lessons, task history, schedules, Apple developer credentials, signing
keys, pairing files, and OS VPN configurations.

- Use a separate backup password, Argon2id key derivation, and authenticated
  encryption.
- Put archive validation, encryption, and migration logic in shared reusable code.
- Keep platform adapters limited to approval UI, secure-store access, and transfer.
- Require phone approval; encrypt sensitive exports before exposing transfer files.
- Preview restoration, validate before modification, preserve the previous state
  until success, and roll back failures.
- Restore account bundles atomically. Recompute readiness and allow
  reauthentication when providers reject expired credentials.
- Map supported preferences across platforms; report unsupported settings rather
  than applying iOS-specific configuration on Android.
- Require explicit confirmation before restoring access permissions.

## 5. Delivery and acceptance

Roadmap items:

- [ ] Shared OpenTelemetry instrumentation and offline persistence.
- [ ] Desktop fetching/export and deterministic diagnosis with repairs.
- [ ] Encrypted settings and AI-account backup/restore.

Deliver tracing first, then diagnostics, then backup.

Acceptance requires:

- Shared JVM and iOS tests plus an Android library test harness; do not wait for
  a complete Android app to verify portability.
- Matching telemetry contracts and diagnostic results for equivalent iOS/Android
  fixtures.
- Cross-language context propagation, cancellation, retries, concurrent
  operations, and crash recovery.
- Evidence separating the current Brain-planning delay from VPN and screen
  startup.
- Redaction tests across logs, traces, metrics, and exports.
- Wrong passwords, tampered backups, interrupted transfers, rollback, and
  unsupported-platform settings.
- OTLP compatibility checks and measured tracing overhead.
- Physical iPhone validation; Android transport validation on a real Android
  device before marking that adapter complete.
