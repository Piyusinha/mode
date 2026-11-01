# Decisions

Each entry gives the context, what was weighed, what was chosen and what it
costs. Newest last. All were made on 2026-10-31 unless noted.

---

## D1. Replace the XCUITest engine with CoreDevice remote control

**Context.** The first engine was an XCUITest session (`EngineTests.testServe`)
running an HTTP server inside Mode.app (commit `33a5c5b`). It worked, with taps
in 0.7 s. But on the phone iOS showed an **"Automation Running"** overlay for
every XCUITest session, which the user did not want. The engine also had to be
launched from a Mac with `xcodebuild` every time.

**Options weighed** (details in [research.md](research.md)):

| Option | Overlay? | Verdict |
|---|---|---|
| Hide the overlay through private XCTest API | — | Rejected. It is iOS's indicator that something else is driving the phone, and patching it out is a security bypass. Fragile too. |
| Deep links, Shortcuts and App Intents | None | Not general: only what each app exposes. Not enough on its own. |
| ESP32 Bluetooth/USB mouse + AssistiveTouch + AirPlay or ReplayKit | Recording indicator | Works, but needs a dongle, uses a relative pointer that has to be calibrated, gets 5–15 fps and types ASCII only. Kept as runner-up. |
| WebDriverAgent / Appium / devicekit-ios | Same overlay | Rejected: all XCTest underneath. |
| **CoreDevice remote control (iOS 27's Device Hub services)** | None | **Chosen.** |

**Chosen.** Use the CoreDevice services Xcode 27's Device Hub uses: HID
touchscreen and keyboard, buttons, screen capture, display stream, pasteboard
and app service. No XCTest is involved.

**Consequences.**
- ✅ No overlay, confirmed on the phone. A "Device was viewed remotely from
  Mac" notification appears after a session; that was accepted.
- ✅ Can run with no Mac at runtime.
- ❌ No accessibility tree, so screen text comes from OCR. No foreground-app id.
- ❌ It is a private, reverse-engineered protocol. iOS updates may change it:
  iOS 26 refuses it, and iOS 27 added the stream gate. The library is pinned.
- ❌ Needs iOS 27, Developer Mode, a pairing file and a mounted disk image.
- The user asked that the old approach be removed first. The baseline was
  committed so it can be recovered (D2).

## D2. Commit a baseline before deleting

Nothing was committed yet, so deleting the old engine would have erased it for
good. The whole tree was committed as `33a5c5b`, then the removal went in as
`57ac25d`.

## D3. Everything runs on the phone

**User's rule.** The Mac is for building, signing and the one-time pairing
only. This is why the disk image must be mounted from the phone after reboots
(pending), and why the XCUITest engine's "launch from Mac" model was dropped.

## D4. Nothing task-specific is hard-coded

**User's rule.** "Whatever I share, AI will be able to do it; we only give the
inputs and get outputs and do the clicking."

- Code provides generic eyes (screenshot, OCR) and hands (ops). The AI decides
  every step.
- The scripted Uber flow planned for the test screen was removed. The hands
  check does only what the user types in.
- The AI's tools will be generated from `Op`.
- Protocol constants are fine: HID codes, the 0..65535 scale, timings.

## D5. Use `idevice` (Rust), with our own FFI

| Option | Verdict |
|---|---|
| pymobiledevice3 (Python) | Great for testing from the Mac (used in Phase 1), but cannot run inside an iOS app |
| Patch idevice's `ffi/` like iphone-use-app does | iphone-use-app is AGPL, so it was read but not copied. Patching upstream FFI ties us to their C surface. |
| **Our own crate `native/modecore` depending on `idevice` (MIT)** | **Chosen.** A small session-shaped C API that holds the stream for the whole session. |

idevice is pinned at commit `8970209`. Build settings come from iphone-use-app's
lessons: LTO off and no bitcode (about 31 MB instead of 200+), `ring` instead
of `aws-lc`, and never `strip -S`.

## D6. One screen stream per session, held open

iOS 27 drops touch and keyboard input unless the same client holds an open
`DisplayService` media stream, and it still reports success. Negotiating a
stream per gesture costs about 1 s and has wedged the media daemon. So the
session opens audio+video once, keeps it, and drains the RTP so memory does not
grow. Measured: touch still works after the session sits idle for 45 s.

## D7. Kotlin cinterop, with the backend in common code

The `PhoneBackend` lives in Kotlin like the rest of the app. The C library is
bound with cinterop, and its `.a` is embedded in the klib. The backend's logic
is in `commonMain` behind small interfaces (`DeviceSession`, `TextReader`,
`ImageTools`), so it is unit-tested on the JVM with a fake phone (14 tests).
Only thin wrappers live in `iosMain`.

## D8. Screen text comes from on-device OCR

There is no accessibility tree over CoreDevice. Apple's Vision framework reads
text on the phone, so nothing leaves it. It returns
`{text, confidence, source:"ocr", x, y, w, h}` in points. That is the same
shape the XCUITest engine returned from the tree, so callers do not change.
Icon-only buttons are left to the model, which sees the screenshot as well.

## D9. Unicode goes through the clipboard

The HID keyboard sends key codes, so it can only type what is on a US keyboard.
Phase 1 showed that putting "इंडिया गेट" on the clipboard and pasting works.
`TypeText` types ASCII and pastes everything else (clipboard + Cmd+V).

Cost: Cmd+V makes iOS ask "<App> would like to paste from dtpasteboardd". The
agent can tap Allow, or the user can allow pasting per app in Settings.
Tapping iOS's own Paste menu never asks.

## D10. PNG screenshots only

iOS 27.2 rejects idevice's `jpeg` format ("Cannot initialize
ScreenshotImageFormat from invalid String value jpeg"). The phone sends PNG
(about 4 MB) and Mode shrinks it on the phone. Taking frames from the
already-open stream is a possible later speed-up.

## D11. Streamed app list

The plain `listapps` call never returns on iOS 27 (it hung for 40 s). The
streamed `streamapplist` returns 238 apps in 42 ms. `LaunchApp("Uber")`
resolves names against this list, which replaced the old ~6 s Spotlight
routine.

## D12. Connect through a loopback VPN (the phone refuses local clients)

**Plan.** Try `127.0.0.1` first (LocalDevVPN#31 showed loopback TCP accepted
even on cellular), and use the VPN only as a fallback.

**Result on the phone.** `127.0.0.1`, the phone's own Wi-Fi address and its own
USB-link address all accepted the TCP connection, then dropped pair-verify. The
same file and code worked from the Mac. So the phone refuses pairing from
clients on the phone itself.

**Chosen.** Connect only through a loopback VPN whose peer `10.7.0.1` the phone
sees as a paired computer (see [flows.md](flows.md#3-why-the-vpn-how-the-phone-sees-each-route)).

**Consequences.**
- It takes iOS's single VPN slot, so Tailscale and similar stop while it is on.
- **Mobile data.** With Wi-Fi off, the phone refuses the VPN route, because it
  treats it as the cellular interface (jkcoxson, StikDebug#259). Workaround:
  turn mobile data off for a moment while connecting.
- Normal internet is untouched: only `10.7.0.1/32` is routed.

## D13. Paid and free builds, both supported

**User's rule.** The user has a paid account, but every plan must also work for
free Apple IDs.

| | Paid | Free |
|---|---|---|
| VPN | `ModeTunnel` extension inside Mode, started automatically | LocalDevVPN app from the App Store (same `10.7.1.1` / `10.7.0.1` defaults) |
| Entitlement | Network Extension (packet tunnel) | none needed |
| Re-signing | about yearly | every 7 days |
| Generate | `tools/gen-project.sh paid` | `tools/gen-project.sh free` |

The two are switched with an XcodeGen include (`project.paid.yml`) and an
Info.plist flag, `ModeBuiltInVPN`. The free build on the phone is tested last,
as the user asked.

## D14. Pairing file delivered with devicectl, or by hand

`tools/make-pairing.sh` pairs as host "Mode" and copies the file into Mode's
`Documents` with `devicectl device copy to`, so no import screen was needed.
Documents is also shared with Finder and the Files app (`UIFileSharingEnabled`),
so the file can be dropped in by hand.

## D15. Faster looks

The first on-phone looks took about 7–8 s. Fixes:

1. Reuse one screenshot for the picture and the text (cleared by any action, or
   after 1.5 s).
2. Decode the PNG once (`DecodedScreens`).
3. OCR a copy shrunk to 1400 px.
4. Warm Vision up at connect time.

Result: 1.4 s for the first look, 0.8 s after.

## D16. `ScreenCapture` takes an optional `max_size`; new `OpFailed`

Models need a small JPEG, not a 4 MB PNG, so `ScreenCapture` became a data
class with an optional `max_size`. Absent, it is left off the wire. `OpFailed`
covers ops that ran but did not work.

## D17. Mic, overlays and auto-lock (discussion)

- **Mic.** Using the mic does not stop auto-lock. It would also break the hands,
  because iOS will not start the screen stream while a foreground app uses the
  camera or mic. Voice input must finish before the agent drives.
- **Overlays.** Third-party apps get no floating overlays. A Live Activity or
  the Dynamic Island shows progress and a Stop button.
- **Auto-lock.** Our touches go through the real input path, so they should
  reset auto-lock while the agent is active. Not yet verified. If the phone
  locks, the agent stops and asks; it never types the passcode.
- **iOS's own indicator.** During a session the Dynamic Island shows a
  screen-sharing icon. Tapping it opens "'Mode' is viewing and controlling
  this iPhone" with **Stop Session**, which also **unpairs** Mode, so
  `make-pairing.sh` has to be run again. This is the acceptable kind of
  indicator, not the "Automation Running" overlay.

## D18. Mode remounts the developer disk image itself (bundled from Xcode)

**Context.** Before iOS 26.4 a restart unmounted the disk image, and only a
computer could mount it again. That breaks the "everything on the phone" rule.

| Option | Verdict |
|---|---|
| Download at runtime from a third-party mirror (`doronz88/DeveloperDiskImage`, as iphone-use-app does) | Depends on a third party |
| **Bundle Apple's own copy from this Mac's Xcode** (`/Library/Developer/DeveloperDiskImages/iOS_DDI`) | **Chosen.** `tools/bundle-ddi.sh` copies the five files the manifest's Cryptex1 identity names (~17 MB) into `Mode.app/DDI`. |

At connect, if the display service is missing, modecore has Apple's TSS sign a
ticket for this phone's nonce and installs the image through `cryptexd`.
**That step needs internet.** Mounting restarts the phone's service directory
and kills the tunnel it ran over, so modecore then reconnects afresh.

**Found on the phone:**
- On iOS 27 the image **survives a restart**.
- When removed, something (the Mac while connected, or the phone itself)
  **restores it within about 5 s**.
- Mode's own mount is the fallback: proven at 2.49 s.

## D19. Keep Mode awake with silent, mixable audio

**Context.** iOS paused Mode about 25 s into a hands check. The phone then
closed the idle connection, and the tap went out on a dead socket.

| Option | Verdict |
|---|---|
| Background-task time (what Mode had) | iOS decides how long, and here it was nearly nothing |
| **Silent audio, `UIBackgroundModes: audio`, mix-with-others** | **Chosen.** No permission prompt, almost no battery. Music keeps playing and nothing shows in Now Playing. Runs only while a task drives another app, and restarts after interruptions. Not allowed on the App Store, which does not matter for a sideloaded app. |
| Background location | Backup: needs a permission prompt and shows an arrow |
| Picture-in-Picture | Maybe later as a status bubble; it would appear in the agent's screenshots |
| Run inside the VPN extension | Paid only, tight memory limits |

Verified: music kept playing, Wi-Fi was tapped, and Mode came back by itself.
The look finished at 3.85 s instead of 27 s.

## D20. Reconnect on drops, but never repeat an action blindly

`ResilientBackend` (core-ops) reconnects when the connection drops. It retries
looks, Home and opening an app. It does **not** re-send a tap, swipe or typing,
because if the drop came after the phone acted, a retry would act twice.
Instead it reports that the action may not have happened, and the caller looks
again (4 tests).

## D21. The agent (Phase 6): OpenRouter, a cheap default, a strong fallback

- **Provider.** OpenRouter's OpenAI-compatible API, as the user chose. The key
  is pasted into Settings and kept in the iOS Keychain (this device only,
  readable after first unlock so background runs work). It is never in code.
- **Default model.** `anthropic/claude-haiku-5.5` ($0.10 / $0.50 per M tokens
  on 2026-10-31, about $0.0004 a step). After two no-progress steps or two bad
  replies, one decision goes to `anthropic/claude-sonnet-5.5`. Both are
  settings. The final default comes from the on-phone comparison (Compare tab).
- **What the model sees each step.** The task; the last 12 steps as text; a
  ~1000 px JPEG; the text on screen with centres in screenshot pixels. One
  tool call is required. Tools are generated from the phone's supported ops.
- **Safety.** The prompt says to ask before paying, booking, sending,
  deleting, or anything irreversible, and to never type passwords. As a
  backstop, `RiskGuard` asks the user before tapping a label containing pay,
  book, order, confirm, request, send, delete and similar words (whole words,
  not tied to any app).
- **Questions.** Mode comes to the front and notifies; the model is told Mode
  is now on screen, so it reopens its app.

## D22. Debug logs for every run

Comparing models and fixing errors needs a full record, so every run writes
`Documents/logs/runs/<time>/`:

- `run.json`: task, model, outcome, steps, time, cost.
- `steps.jsonl`: per step, the look (ms, text count), the exact prompt, the
  model's raw tool call with tokens, cost and ms (or its error), and the action
  with its outcome and ms.
- `step-N.jpg`: what the model saw.

App events (connecting, routes tried, disk-image mounts, reconnects,
keep-awake, VPN, questions, errors) go to `logs/events.log` and to the system
log. Comparisons write `logs/compare/<id>.txt`.

- See them in the Logs tab, in Files › On My iPhone › Mode › logs, or on a Mac
  with `tools/pull-logs.sh`.
- The newest 60 runs are kept.

## D23. Hold the session only while working

The Dynamic Island's "viewing and controlling" icon and the VPN icon stayed
after Mode was closed, because the session was held open forever. Now Mode
ends the session (the screen stream stops, Mode's VPN goes off) when a task,
comparison or hands check ends, and whenever the user leaves Mode while it is
idle. Each task then reconnects, which costs about 1 s. Cleanup runs even
after Stop.

## D24. Modular clean architecture: one module per feature

Mode is becoming a production app (SwiftUI screens, Live Activity, Siri,
memory, a Mac setup app, Android later). The app logic had grown inside the
Compose UI module, mixed with Compose state that SwiftUI cannot use. It is
now split into layers (core → engine → feature → app) with one Gradle module
per feature, each with `domain` / `data` / `presentation` packages, an
explicit public API, and `StateFlow` state. Convention plugins in
`build-logic` keep every module's build to a few lines, and
`checkModuleRules` fails the build if a dependency points up a layer.

A feature is one module, not an `api` + `impl` pair: `internal` hides the
implementation, and splitting later is cheap if build times call for it.

## D25. Native UI per platform over a shared presentation layer

The iOS screens will be SwiftUI (Liquid Glass, Live Activities, App Intents
and widgets need Swift anyway), and Android's will be Jetpack Compose. Both
use the same Kotlin features through their state holders. SKIE makes Kotlin
`StateFlow`, sealed types and `suspend` functions natural in Swift. The
Compose screens stay as `app:dev-ui` until SwiftUI replaces them.

## D26. Manual dependency injection

`ModeGraph` constructs every feature once, with constructor injection, from
the `PlatformServices` a platform provides. It needs no library, the compiler
checks it, and Swift uses it directly. Koin (or similar) can come later if
the graph grows past what one file reads comfortably.

## D27. Brand: the Orbit icon and Instrument Sans

- **Icon, "Orbit":** a frosted glass iPhone with an agent circling it; the
  orbit passes behind the phone and in front of it, and the agent leaves a
  trail. A first try (an M drawn as one swipe) read too much like Gmail.
  `tools/make-icon.swift` draws it at 1024 px in light, dark and tinted into
  `iosApp/Mode/Assets.xcassets`; iOS adds its own glass.
- **Typeface:** Instrument Sans (SIL Open Font License, `iosApp/Mode/Fonts/OFL.txt`)
  for titles and headings, chosen as the closest free match to Anthropic Sans
  Display, which can't ship. Body text stays SF Pro so it follows the iPhone's
  text size. SwiftUI uses `Font.mode(.largeTitle)` from `ModeFont.swift`.

## D28. Notes as a field, and a check before done that runs once

- **Notes.** The harness design had `note(text)` as its own tool. It became an
  optional `note` field on every action instead: a model reading a calendar
  can scroll and keep what it just read in the same step. A separate tool
  would cost one more model call per fact, about $0.0005 and 2 s each.
- **Verify.** `done` must give evidence: what on the current screen shows the
  task is complete. A successful `done` is sent back for a look when the
  evidence is missing or the last action changed nothing. This happens only
  once per task, and the second `done` is always accepted. That catches "I
  tapped Send" when nothing was sent, without a loop, and without asking the
  user anything (the user wants as few questions as possible).
- **Stuck.** Stopping early with an honest reason ("Stuck: the screen stopped
  changing after 4 tries. Last: tap "Send".") beats spending the rest of the
  step and cost budget going round in circles.

## D29. Memory: facts, skills and tips, learned by an outside critic

- **Three kinds of memory:**
  - **facts** about the user, all of them shown up to 25;
  - **skills**, routes that worked, with placeholders;
  - **app tips**.

  These follow the 2026 mobile-agent research (EvoSkill-GUI, MAGNET) and
  ChatGPT's saved memories. Matching uses words, not embeddings: it's fast,
  on-device and enough for a few hundred items.
- **Writing:** after each task, one call to the reflection model acting as an
  outside critic. It sees the steps and the final screen, not the agent's
  reasoning. The harness has the last word:
  - evidence on every change;
  - new skills only from successes;
  - a failure may only add to a skill;
  - at most 5 changes;
  - no secrets (`SecretFilter`);
  - near-duplicates merged.
- **Saving:** memory saves without asking; new facts show "Remembered · Undo".
  Private tasks and comparison runs neither use nor add to memory.
- **Not yet:** memory lives only on the iPhone. Expiry by confidence comes in
  H5.

## D30. One inbox, one queue, and the iOS limit on schedules

- **Every source calls `TaskInbox.submit`:** the app now; Siri, Shortcuts, the
  Watch and schedules next. One drain loop runs tasks in order under a phone
  lock, so a comparison run and a queued task never collide.
- **At launch:**
  - tasks interrupted by Mode closing are marked failed;
  - queued tasks more than an hour old are not run, since a stale "send it
    now" is worse than none.
- **Schedules:** iOS won't let Mode start itself in the background. So a
  schedule runs while Mode is open, and otherwise sets a notification that
  opens Mode, which then runs it. A run missed by more than 2 hours is
  skipped. Waking in the background is tested last (status, "Last").

