# Mode

**Tell your iPhone what to do. Mode does it in your apps.**

Mode is an AI agent that runs on your iPhone. You say or type a task, such as
“Turn off Dark Mode” or “Text Asha I’m on my way on WhatsApp”, and Mode looks at
the screen, taps, types and opens apps until it is done, then tells you how it
went. It uses the AI you already have (a ChatGPT or Claude plan, or an
OpenRouter key), and it is open source.

- **Runs on your iPhone.** No Mode server: Mode talks only to the AI and voice
  providers you choose.
- **Your own AI.** Continue with ChatGPT or Claude (your plan, no extra cost per
  task), or bring an OpenRouter key and pick any model.
- **Talk to it.** “Hey Siri, start Mode”; Mode answers out loud. Apple’s voice and
  listening are free and private; ElevenLabs, Fish Audio, Deepgram and Soniox are
  optional with your own key.
- **It learns.** Correct it while it works (“search for it instead”) and it can
  remember that for next time. Memory stays on the iPhone, and you can forget
  anything.
- **No scripts.** Nothing in Mode is written for a particular app. The AI decides
  every step from your task and what is on the screen.

> Mode controls the phone through the remote-control services Apple added to
> iOS 27 for developers (CoreDevice). It needs Developer Mode, and a computer
> once to install it and pair it.

## What you need

| | |
|---|---|
| iPhone | iOS 27 or later, Developer Mode on |
| Computer | A Mac, Windows or Linux computer, once, for Mode Setup (and every 7 days on a free Apple account, unless you add SideStore) |
| Apple account | Free or paid developer account. Free: Mode is signed for 7 days at a time. Paid ($99/year): signed for a year, and Mode uses its own VPN |
| AI | A ChatGPT plan, a Claude plan, or an OpenRouter key |

## Install

1. Download **Mode Setup** for your computer from the
   [latest release](https://github.com/Piyusinha/mode/releases/latest).
2. Connect your iPhone with a cable and follow Mode Setup: trust this computer,
   turn on Developer Mode, sign in with your Apple account (the password goes
   only to Apple), and Mode Setup signs and installs Mode and pairs it.
3. Open Mode on the iPhone and finish setup: your name, your AI, a quick test.

On a free Apple account, Mode Setup can also install
[SideStore](https://sidestore.io), which renews Mode every week on the iPhone so
you don’t need the computer again.

## Build from source

See [docs/setup.md](docs/setup.md). In short:

```sh
tools/gen-project.sh paid             # or: free (generates iosApp/Mode.xcodeproj)
./gradlew jvmTest checkModuleRules    # shared Kotlin tests and module rules
open iosApp/Mode.xcodeproj            # build and run on your iPhone
cd desktop && cargo build             # Mode Setup (Rust + Tauri)
```

Set your own team in `iosApp/project.yml` (`DEVELOPMENT_TEAM`) before building.

## How it works

The agent, memory, task queue, model clients and voice are shared Kotlin
(Kotlin Multiplatform). The iPhone app is SwiftUI. A small Rust library talks to
the phone’s CoreDevice services for screenshots, taps, typing and opening apps.
Mode Setup is a Rust and Tauri app built on
[isideload](https://github.com/nab138/isideload) and
[idevice](https://github.com/jkcoxson/idevice).

| Document | What it covers |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, modules and data |
| [docs/harness.md](docs/harness.md) | How the agent thinks, remembers and stays within budget |
| [docs/flows.md](docs/flows.md) | Pairing, connecting, a look, a tap, the agent loop |
| [docs/decisions.md](docs/decisions.md) | Every design decision and why |
| [docs/status.md](docs/status.md) | What is done and what is next |

## Privacy

Mode keeps your tasks, chats and memory on your iPhone. Screenshots of the
current screen and your task go to the AI provider you chose, and your voice to
the voice provider you chose (Apple’s stay on the iPhone). Keys and sign-ins are
kept in the iPhone’s Keychain. Details: [PRIVACY.md](PRIVACY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).

Mode is not affiliated with Apple, OpenAI, Anthropic, OpenRouter or any voice
provider. iPhone and iOS are trademarks of Apple Inc.
