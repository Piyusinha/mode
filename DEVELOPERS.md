# Mode for developers

How Mode is built, how to build it yourself, and where everything lives. If you want to
send a change, read [CONTRIBUTING.md](CONTRIBUTING.md) too.

## The idea

Mode is an AI agent on the iPhone. You say a task; the agent looks at the screen, decides
the next step, taps, types and opens apps until the task is done, then reports back.
Nothing in Mode is written for a particular app: the AI decides every step from the task
and what is on the screen.

Mode controls the phone through the remote-control services Apple added to iOS 27 for
developers (CoreDevice). That is why it needs Developer Mode, and a computer once to
install it and pair it.

## How it’s built

| Part | Built with | Where |
|---|---|---|
| The agent, memory, task queue, schedules, model clients, voice | Kotlin Multiplatform (shared with the coming Android app) | `core/`, `engine/`, `feature/`, `app/` |
| The iPhone app, Live Activity, Siri intents | SwiftUI, App Intents, ActivityKit | `iosApp/` |
| Design system (tokens and components only) | Swift package | `iosApp/Packages/ModeDesignSystem` |
| Phone control: screenshots, taps, typing, opening apps | Rust, over CoreDevice | `native/` |
| Mode Setup for Mac, Windows and Linux | Rust and Tauri, on [isideload](https://github.com/nab138/isideload) and [idevice](https://github.com/jkcoxson/idevice) | `desktop/` |

The Kotlin side is layered `core → engine → feature → app`; `./gradlew checkModuleRules`
enforces who may depend on whom. Screens are dumb: Kotlin presenters in `app/screens`
hold all the logic and state, and SwiftUI views only render that state and forward taps.

## Build from source

You’ll need a Mac with Xcode 27.1+, XcodeGen (`brew install xcodegen`), Rust via rustup,
an iPhone on iOS 27+ with Developer Mode on, and an Apple account (free or paid). The full
walkthrough, including pairing and the free-account VPN, is in [docs/setup.md](docs/setup.md).

```sh
tools/gen-project.sh paid             # or: free (generates iosApp/Mode.xcodeproj)
./gradlew jvmTest checkModuleRules    # shared Kotlin tests and module rules
sh tools/check-ui-literals.sh         # screens use design-system tokens only
open iosApp/Mode.xcodeproj            # build and run on your iPhone
cd desktop && cargo build             # Mode Setup (Rust + Tauri)
```

Set your own team in `iosApp/project.yml` (`DEVELOPMENT_TEAM`) and a bundle ID prefix of
your own before building for a device.

## Tests

- `./gradlew jvmTest` runs the shared Kotlin tests (agent, memory, queue, presenters,
  backup). They run on the JVM with fakes for the phone and the AI, so they need no device.
- `./gradlew checkModuleRules` checks the module layering.
- `sh tools/check-ui-literals.sh` fails on literal colours, sizes, fonts or strings in
  screens. It also runs as an Xcode build phase.
- `node --test desktop/setup-app/tests/phone-setup.test.cjs` and `cargo test` cover Mode Setup.
- [docs/eval-suite.md](docs/eval-suite.md) is the on-phone suite of real tasks used to
  judge the agent.

## Releasing

`tools/release.sh <version>` builds the iPhone app files and Mode Setup for Mac on a Mac,
notarises the Mac app when signing is set up, and makes a draft GitHub release. The
`Release` workflow then adds the Windows and Linux installers. The README’s download
button points at `releases/latest/download/Mode-Setup-mac.dmg`.

## Documents

| Document | What it covers |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, modules, data shapes, threading, build |
| [docs/harness.md](docs/harness.md) | How the agent thinks, remembers and stays within budget |
| [docs/flows.md](docs/flows.md) | Pairing, connecting, a look, a tap, the agent loop |
| [docs/coredevice.md](docs/coredevice.md) | The CoreDevice services Mode uses, timings and gotchas |
| [docs/decisions.md](docs/decisions.md) | Every design decision and why |
| [docs/setup.md](docs/setup.md) | Build, pair, install and test, for paid and free accounts |
| [docs/observability.md](docs/observability.md) | Local OpenTelemetry tracing |
| [docs/status.md](docs/status.md) | What is done and what is next |

## Privacy and security

Mode has no server. Read [PRIVACY.md](PRIVACY.md) for what leaves the phone and to whom,
and [SECURITY.md](SECURITY.md) to report a vulnerability privately.
