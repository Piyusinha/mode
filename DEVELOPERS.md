# Mode for developers

What actually happens inside Mode when you say *“Text Sam I’m on my way”*, how it
sees the screen and taps, how it keeps going when things go wrong, and how to build
it yourself. If you want to send a change, read [CONTRIBUTING.md](CONTRIBUTING.md) too.

**Contents**
1. [The big picture](#1-the-big-picture)
2. [Setup, once: pairing with Device Hub](#2-setup-once-pairing-with-device-hub)
3. [Connecting: how Mode reaches its own iPhone](#3-connecting-how-mode-reaches-its-own-iphone)
4. [One task, start to finish](#4-one-task-start-to-finish)
5. [One step: look, think, act, check](#5-one-step-look-think-act-check)
6. [Seeing the screen](#6-seeing-the-screen)
7. [Tapping, scrolling and typing](#7-tapping-scrolling-and-typing)
8. [When something goes wrong](#8-when-something-goes-wrong)
9. [Talking: Hey Siri, start Mode](#9-talking-hey-siri-start-mode)
10. [Memory: how Mode learns you](#10-memory-how-mode-learns-you)
11. [Where everything lives](#11-where-everything-lives)
12. [Build from source](#12-build-from-source) · [Tests](#13-tests) · [Releasing](#14-releasing) · [More documents](#15-more-documents)

---

## 1. The big picture

Mode is an app on your iPhone that drives your iPhone. It has **eyes** (a picture of
the screen plus the words on it), **hands** (taps, swipes, typing, opening apps) and a
**brain** (the AI you connected). Nothing in Mode knows how WhatsApp or Settings work:
the AI decides every step from your task and what is on the screen.

```mermaid
flowchart LR
    you(["🗣️ You<br/>voice, typing, Siri,<br/>a repeat, Shortcuts"]) --> queue["Task queue<br/>one task at a time"]
    queue --> harness

    subgraph phone["Everything runs on the iPhone"]
        harness["🧠 Harness<br/>plans, keeps notes,<br/>checks every step,<br/>recovers, verifies"]
        memory[("Memory<br/>people, usuals,<br/>app know-how")]
        hub["📱 Device Hub<br/>iOS 27 developer services"]
        harness <-->|reads before,<br/>learns after| memory
        harness -->|"look"| hub
        hub -->|"screenshot + text"| harness
        harness -->|"tap, type, open"| hub
    end

    harness <-->|"task + screen,<br/>next action"| ai["☁️ Your AI<br/>ChatGPT, Claude<br/>or OpenRouter"]
    harness --> out(["✅ The answer<br/>chat, voice,<br/>Lock Screen, Dynamic Island"])
```

There is no Mode server. The only things that leave the phone are the task, the
current screen and a few memory lines (to the AI you chose) and your voice (to the
voice service you chose; Apple’s stays on the phone).

---

## 2. Setup, once: pairing with Device Hub

iOS 27 has **Device Hub**: Apple’s developer services (CoreDevice) that let a trusted
computer watch the screen and send touches, for testing apps. Mode uses those same
services, but from the phone itself. For that, the phone must trust Mode once, and
the iPhone only creates that trust **over a USB cable from a computer**. Mode Setup
does it (Mac today; the same code builds for Windows and Linux).

```mermaid
sequenceDiagram
    autonumber
    actor You
    participant Setup as Mode Setup (computer)
    participant Phone as iPhone (USB)
    participant Mode as Mode app

    You->>Phone: Turn on Developer Mode
    You->>Setup: Plug in the iPhone, sign in with your Apple account
    Setup->>Phone: Sign Mode with your account and install it
    Setup->>Phone: Ask to pair as "Mode"
    Phone-->>You: "Trust This Computer?"
    You->>Phone: Trust
    Phone-->>Setup: Pairing file (a few hundred bytes)
    Setup->>Mode: Copy the pairing file into Mode's own folder
    Note over Mode: From now on Mode needs no computer.<br/>It uses this file to reach Device Hub itself.
```

The pairing file is lost if Developer Mode is turned off, the phone’s trust settings
are reset, or you tap **Stop Session** on the Dynamic Island during a task. Then
Mode Setup pairs again.

*Code:* `desktop/` (Mode Setup, Rust + Tauri, on isideload and idevice),
`native/modecore` (`pair_over_usb`).

---

## 3. Connecting: how Mode reaches its own iPhone

Device Hub refuses connections that start on the phone itself. It wants a
“computer”. So Mode runs a tiny **loopback VPN** that sends its own packets straight
back to the phone with the sender and receiver swapped. Device Hub then sees a
connection from another address, checks the pairing file, and accepts it.

```mermaid
sequenceDiagram
    autonumber
    participant App as Mode
    participant VPN as Loopback VPN<br/>(Mode's own on paid accounts,<br/>LocalDevVPN on free ones)
    participant Hub as Device Hub<br/>(remote pairing, port 49152)

    App->>VPN: Connect to 10.7.0.1:49152
    VPN->>Hub: Same packet, addresses swapped
    Hub-->>App: "Who are you?"
    App->>Hub: Proof from the pairing file
    Hub-->>App: Trusted. Here are the services
    App->>Hub: Open the screen stream (eyes)
    App->>Hub: Open touch, buttons and keyboard (hands)
    Note over App,Hub: About 1 second when it has connected before.<br/>If the developer disk image is missing,<br/>Mode installs its bundled copy first.
```

While a task runs, Mode plays silent audio so iOS keeps it awake while another app is
in front; otherwise the phone drops the idle connection. When the task ends, Mode
closes the connection so the microphone works again (iOS gives an app a silent
microphone while the screen is being shared).

*Code:* `engine/phone-ios/.../PhoneConnector.kt`, `LoopbackVpn.kt`,
`ModecoreSession.kt`, `iosApp/ModeTunnel/PacketTunnelProvider.swift`,
`feature/connection/.../PhoneSession.kt`.

---

## 4. One task, start to finish

```mermaid
flowchart TB
    start(["You: 'Reply to Sam I'm on my way,<br/>then check my calendar'"]) --> q["1 · Queue<br/>from the app, Siri,<br/>a repeat or Shortcuts"]
    q --> u["2 · Understand<br/>pull the memory this task needs:<br/>Sam is on WhatsApp, your usuals"]
    u --> d{"3 · A direct route?<br/>calendar, contacts, a link,<br/>a Shortcut, a web lookup"}
    d -- "yes: no tapping needed" --> v
    d -- "no, or only partly" --> p["4 · Plan<br/>the brain writes short steps:<br/>open WhatsApp → Sam → send → calendar"]
    p --> loop["5 · Act loop<br/>look → think → act → check<br/>(section 5), repeated"]
    loop --> v{"6 · Verify<br/>does the screen prove it?<br/>'Delivered', the event list…"}
    v -- "not proven" --> loop
    v -- "proven, or truly blocked" --> r["7 · Report<br/>chat reply, spoken answer,<br/>Lock Screen, notification"]
    r --> m["8 · Reflect, afterwards<br/>a cheap model proposes what to remember;<br/>a filter drops anything secret"]
    m --> e(["Done. In a voice conversation,<br/>Mo asks what's next"])
```

- **One phone, one task at a time.** Tasks from every source wait in one queue.
- **Two minds.** A fast *phone-control* model picks each action. A stronger *brain*
  writes the plan and is consulted when Mode is stuck or about to give up.
- **Direct routes first.** If the answer is in the calendar, contacts, a web lookup
  or a Shortcut, Mode uses that instead of tapping through screens. The calendar is
  treated as Google Calendar unless you name another one.
- **Stops:** a step budget that grows with progress, a cost limit (OpenRouter), being
  stuck after every recovery idea, 15 minutes, or your Stop. Each ends with an honest
  summary of how far it got.

*Code:* `feature/tasks/.../TaskQueue.kt`, `TaskRunner.kt`, `engine/agent/Agent.kt`,
`Brain.kt`, `engine/ops/DeviceData.kt`, `feature/memory/.../Reflector.kt`.

---

## 5. One step: look, think, act, check

Every step of the act loop is the same small cycle. A step takes about 3–5 seconds:
around 1 s to look, 1–3 s for the AI, around 1 s to act and let the screen settle.

```mermaid
flowchart LR
    look["👀 Look<br/>screenshot +<br/>words with positions"] --> prompt["📝 Build the prompt<br/>rules · you · app notes ·<br/>task · plan · notes ·<br/>last steps · the screen"]
    prompt --> ai["☁️ AI picks ONE action<br/>and says what it expects<br/>('the chat with Sam opens')"]
    ai --> act["👆 Act<br/>tap, scroll, type,<br/>open an app, press a key"]
    act --> check{"✔️ Did the screen<br/>do what it expected?"}
    check -- "yes: progress" --> look
    check -- "no" --> recover["🔁 Recovery ladder<br/>(section 8)"]
    recover --> look
    ai -- "needs you" --> ask(["🙋 Ask you:<br/>which size? which address?"])
    ai -- "done" --> verify(["Verify, then report"])
```

**What the AI sees each step** is built in fixed sections, most stable first, so
providers that cache prompts can reuse most of it:

| # | Section | Changes |
|---|---|---|
| 1 | Rules and iOS know-how | Never |
| 2 | The tools it may call | Never |
| 3 | About you: matching memory, your name | Per task |
| 4 | Notes for the apps in this task | Per task |
| 5 | Earlier tasks in this chat | Per task |
| 6 | The task, your answers, the plan | Rarely |
| 7 | Facts it noted on the way (“flight 6E223”) | Grows, capped |
| 8 | The last 12 steps, earlier ones summarised | Every step |
| 9 | The screen: picture + up to 80 words with positions | Every step |

*Code:* `engine/agent/ContextBuilder.kt`, `AgentTools.kt`, `ExpectCheck.kt`,
`StepBudget.kt`.

---

## 6. Seeing the screen

```mermaid
sequenceDiagram
    autonumber
    participant Agent
    participant Eyes as CoreDeviceBackend
    participant Hub as Device Hub screen stream
    participant OCR as Apple Vision (on the phone)

    Agent->>Eyes: Look
    Eyes->>Hub: Capture the screen
    Hub-->>Eyes: Full-size picture (1206 × 2622)
    Eyes->>Eyes: Decode once, shrink to 1000 px, JPEG
    Eyes->>OCR: Read the words (same picture, 1400 px)
    OCR-->>Eyes: Each word with its box on screen
    Eyes-->>Agent: Picture + words in screen points
    Note over Agent,OCR: About 0.8 s per look (1.4 s the first time).<br/>There is no accessibility tree:<br/>the words come from on-device text recognition.
```

The AI gets both: the picture (to understand the layout) and the words with exact
positions (so a tap on “Send” lands on Send). A picture taken before the last action
is never reused.

*Code:* `engine/phone-ios/.../CoreDeviceBackend.kt`, `VisionTextReader.kt`.

---

## 7. Tapping, scrolling and typing

```mermaid
flowchart TB
    action{"The AI's action"} -->|"tap(x, y)"| tap["Map screen points to the touch grid<br/>finger down → 50 ms → finger up"]
    action -->|"scroll"| scroll["Finger down, move in 16 ms frames,<br/>rest 100 ms so the list stops, lift"]
    action -->|"type(text)"| kind{"Every character on<br/>an English keyboard?"}
    kind -- "yes" --> keys["Key presses on a virtual<br/>keyboard, Shift where needed"]
    kind -- "no: Hindi, accents, emoji" --> paste["Put the text on the clipboard,<br/>then Cmd+V"]
    action -->|"open_app('WhatsApp')"| open["Launch the app by name"]
    action -->|"home / press_key"| keysys["Home button, Return, volume…"]
    tap & scroll & keys & paste & open & keysys --> settle["Wait for the screen to settle,<br/>then look again"]
```

Touches are timed with the phone’s own clock (`mach_absolute_time`): wall-clock times
make iOS drop taps. Touches are only accepted while Mode’s screen stream is open,
which is why the eyes connect first.

*Code:* `native/modecore` (Rust, over idevice), `engine/phone-ios/.../ModecoreSession.kt`.

---

## 8. When something goes wrong

Most failures come from the harness, not the AI, so the harness fights to finish
before it gives up:

```mermaid
flowchart TB
    miss["A step didn't do what was expected,<br/>or nothing changed"] --> r1["Rung 1 · same action twice, no change<br/>→ 'try another way' (no AI call)"]
    r1 -->|still stuck| r2["Rung 2 · the brain names another route,<br/>told which ones failed"]
    r2 -->|still stuck| r3["Rung 3 · the brain rewrites the rest of the plan"]
    r3 -->|still stuck| r4["Rung 4 · ask you, with options,<br/>or hand the phone back"]

    giveup["The AI wants to give up"] --> gate{"Give-up gate"}
    gate -- "a login or payment" --> hand(["Hand over to you:<br/>Mode never types passcodes<br/>or card numbers"])
    gate -- "closed, unavailable,<br/>needs your choice" --> askyou(["Ask you with options first"])
    gate -- "steps left, one route tried,<br/>or no proof on screen" --> refuse["Refused once:<br/>the brain looks for another way"]
    gate -- "proof quoted from the screen" --> accept(["Accepted: an honest<br/>'couldn't finish' with why"])

    stall["Same picture after 3 actions<br/>that can't all do nothing"] --> reconnect["Phone control stalled:<br/>reconnect, look again, retry the step"]
```

Orders, payments, bookings and sends are **checked twice**: the amounts go into notes
before the final tap, and success needs proof on screen (an order number, an ETA,
“Delivered”). Seeing the same button again means it didn’t go through.

*Code:* `engine/agent/RecoveryLadder.kt`, `GiveUpGate.kt`, `StuckDetector.kt`,
`ChangeLedger.kt`, `Playbook.kt`.

---

## 9. Talking: Hey Siri, start Mode

A spoken session is one conversation: one task after another until you say “that’s
all” or go quiet.

```mermaid
sequenceDiagram
    autonumber
    actor You
    participant Talk as Talking screen
    participant AI as Fast model (lowest effort)
    participant Runner as Task runner
    participant Hub as Device Hub

    You->>Talk: "Hey Siri, start Mode"
    Talk->>AI: Your name, the time, what's coming up today
    AI-->>Talk: A greeting, maybe an offer from your day
    Talk-->>You: Says it, then listens (your words appear live)
    You->>Talk: "Reply to Sam I'm on my way"
    Talk->>Runner: Start the task (same chat as the conversation)
    Talk-->>You: "Sure, replying to Sam on WhatsApp"
    Note over Talk,Hub: The mic is off while Mode works in your apps<br/>(a shared screen gives apps a silent mic)
    loop While it works
        Runner->>Hub: look, act, check
        Runner-->>You: Spoken updates with what it found,<br/>logged on the task's card
    end
    Runner-->>Talk: Result: headline + a row per part
    Talk->>AI: What to say next
    AI-->>Talk: "Anything else?" or an offer to fix what failed
    Talk-->>You: Says it, listens again
    You->>Talk: "That's all" (or 20 s of quiet)
```

*Code:* `app/screens/.../settings/ListenPresenter.kt`, `feature/memory/.../Greeter.kt`,
`Acknowledger.kt`, `feature/tasks/.../Narrator.kt`, `iosApp/Mode/Features/Talk/Talk.swift`.

---

## 10. Memory: how Mode learns you

```mermaid
flowchart TB
    task["1 · A new task: its people,<br/>apps and words"] --> match["2 · Match memory<br/>keywords and tags, no AI call"]
    match --> prompt["3 · Into the prompt:<br/>up to 25 lines about you<br/>and 10 app notes"]
    prompt --> run["4 · The task runs"]
    run --> reflect["5 · Afterwards, a cheap model reads<br/>the task, plan and steps (no screenshots)<br/>and proposes: add, update or forget,<br/>each with evidence"]
    reflect --> filter["6 · Secret filter drops passwords,<br/>codes, card and ID numbers"]
    filter --> store[("7 · Memory on the phone<br/>'Remembered: Sam is on WhatsApp · Undo'")]
    store -.->|"used by the next task"| match
```

| Kind | Example |
|---|---|
| About you | “Sam: message on WhatsApp”, “messages short and casual” |
| How to do it, per app | “Settings: search ‘Dark Mode’ goes straight there” |
| Usuals | “Usual coffee: Oat Latte, Large, to the office” |
| What happened | Past runs, searchable |

You can see, edit and forget everything in Settings → What Mode knows about you.
Backup and restore carries memory to a new phone in one file (encrypted when it
includes your sign-ins).

*Code:* `feature/memory/` (`MemoryRetrieval.kt`, `Reflector.kt`),
`feature/tasks/.../TaskMemory.kt`, `feature/backup/`.

---

## 11. Where everything lives

| Part | Built with | Where |
|---|---|---|
| Agent, harness, memory, queue, schedules, model clients, voice | Kotlin Multiplatform, shared with the coming Android app | `core/` → `engine/` → `feature/` → `app/` |
| Screens’ logic (state, decisions) | Kotlin presenters, tested on the JVM | `app/screens` |
| The iPhone app, Live Activity, Siri | SwiftUI, App Intents, ActivityKit; views only draw state | `iosApp/` |
| Design system (tokens and components only) | Swift package | `iosApp/Packages/ModeDesignSystem` |
| Eyes and hands over Device Hub | Rust on [idevice](https://github.com/jkcoxson/idevice) | `native/modecore` |
| The loopback VPN (paid accounts) | Network Extension | `iosApp/ModeTunnel` |
| Mode Setup for Mac, Windows and Linux | Rust and Tauri, on [isideload](https://github.com/nab138/isideload) | `desktop/` |

The Kotlin layers only depend downwards (`core → engine → feature → app`);
`./gradlew checkModuleRules` enforces it.

| Measured on an iPhone 17 Pro | Time |
|---|---|
| Connect to Device Hub (after the first time) | ~1 s |
| One look: picture + words | ~0.8 s (1.4 s first) |
| Open an app by name | ~0.15 s |
| One step: look + AI + act | 3–5 s |

---

## 12. Build from source

You’ll need a Mac with Xcode 27.1+, XcodeGen (`brew install xcodegen`), Rust via
rustup, an iPhone on iOS 27+ with Developer Mode on, and an Apple account (free or
paid). The full walkthrough, including pairing and the free-account VPN, is in
[docs/setup.md](docs/setup.md).

```sh
cp iosApp/Signing.example.env iosApp/Signing.env   # then put in your team and app ID prefix
tools/gen-project.sh paid             # or: free (generates iosApp/Mode.xcodeproj)
./gradlew jvmTest checkModuleRules    # shared Kotlin tests and module rules
sh tools/check-ui-literals.sh         # screens use design-system tokens only
open iosApp/Mode.xcodeproj            # build and run on your iPhone
cd desktop && cargo build             # Mode Setup (Rust + Tauri)
```

`iosApp/Signing.env` is git-ignored, so your team and app ID never end up in a pull
request.

## 13. Tests

- `./gradlew jvmTest` runs the shared Kotlin tests: the agent, memory, queue,
  presenters and backup. They use fakes for the phone and the AI, so they need no device.
- `./gradlew checkModuleRules` checks the module layering.
- `sh tools/check-ui-literals.sh` fails on literal colours, sizes, fonts or strings in
  screens. It also runs as an Xcode build phase.
- `node --test desktop/setup-app/tests/phone-setup.test.cjs` and `cargo test` cover Mode Setup.
- [docs/eval-suite.md](docs/eval-suite.md) is the on-phone suite of real tasks used to
  judge the agent.

## 14. Releasing

Mode and Mode Setup are released separately, each with its own version and changelog.

| | Mode (the phone app) | Mode Setup (the desktop app) |
|---|---|---|
| Tag and title | `Mode-Mobile-Build-<version>` | `Mode-Desktop-App-<version>` |
| Version in | `iosApp/project.yml` (`MARKETING_VERSION`) | `desktop/setup-app/tauri.conf.json`, `desktop/Cargo.toml` |
| Changelog | `CHANGELOG.md` | `desktop/CHANGELOG.md` |
| Release | `tools/release-app.sh <version>` | `tools/release-setup.sh <version>` |
| Holds | `Mode.ipa`, `Mode-paid.ipa` (and `mode-android.apk` for Android) | the Mac `.dmg`; CI adds Windows and Linux |
| Publish | `gh release edit Mode-Mobile-Build-<v> --draft=false --latest` | `gh release edit Mode-Desktop-App-<v> --draft=false --latest=false` |

- Versions count 0.1.8, 0.1.9, 0.2.0: three single digits, so a part never reaches 10 (0.2.0
  follows 0.1.9, never 0.1.10). The scripts refuse anything else.
- Each script stops before building if the version doesn't match or its changelog has no
  section for it. The section becomes the release's "What's new" (`tools/release-notes.sh`).
- A Mode release is GitHub's "Latest". Mode Setup finds the newest Mode by its tag, installs it
  on every install, reinstall and renewal, and offers it as an update. No new Mode Setup is
  needed for a new Mode.
- A Mode Setup release carries the newest published Mode inside it
  (`tools/latest-app-release.sh`), so it installs offline too.
- heymode.app's download links serve the newest Mode Setup release (`website/worker`).

## 14b. Android

The Android app is `app/android` (Compose over the same shared presenters as the iPhone), its design
library `app/design`, and the engine `engine/phone-android` (Mode's accessibility service). Background
mode adds `engine/adb-link` and `engine/adb-helper`. Everything, from setup to background mode, is in
[docs/android.md](docs/android.md).

```sh
./gradlew :app:android:installFullDebug      # onto a phone or emulator with USB debugging
tools/build-apk.sh                           # a release APK into dist/ (signing: see docs/android.md)
```

Debug builds have an adb probe (`DevProbe`) that drives the engine, posts sample notifications and
walks setup on an emulator; docs/android.md lists its commands.

## 15. More documents

| Document | What it covers |
|---|---|
| [docs/harness.md](docs/harness.md) | The harness in depth: context, memory, budgets, recovery |
| [docs/flows.md](docs/flows.md) | Low-level sequences: pairing, connecting, a look, a tap, typing |
| [docs/coredevice.md](docs/coredevice.md) | The Device Hub services Mode uses, measured timings and gotchas |
| [docs/architecture.md](docs/architecture.md) | Layers, modules, data shapes, threading, build |
| [docs/decisions.md](docs/decisions.md) | Every design decision and why |
| [docs/setup.md](docs/setup.md) | Build, pair, install and test, for paid and free accounts |
| [docs/observability.md](docs/observability.md) | Local OpenTelemetry tracing |
| [docs/status.md](docs/status.md) | What is done and what is next |

Mode has no server. Read [PRIVACY.md](PRIVACY.md) for what leaves the phone and to
whom, and [SECURITY.md](SECURITY.md) to report a vulnerability privately.
