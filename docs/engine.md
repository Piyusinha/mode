# The engine

The engine is how Mode sees and taps other apps. iOS allows that only from an
XCUITest session, so the engine is one test, `EngineTests.testServe`, that runs a
small HTTP server until it is told to stop or goes idle.

Source: `iosApp/ModeEngine/`. Measured on an iPhone 17 Pro, iOS 27.2, Xcode 27.1.

## Wire

phone-harness's op protocol, so the same client code drives either:

```
GET  /ops                       -> {"ops": [...]}
POST /op {"op": name, "kw": {}} -> {"result": ...}
errors                          -> {"error": message}  (+ "unsupported": true)
```

Every request sends `Authorization: Bearer <token>`. It listens on loopback only,
port 47610, unless started with `MODE_ENGINE_BIND_ALL=1` for testing from a Mac.

Ops: `tree`, `screen.text`, `screen.capture`, `screen.bounds`, `input.tap`,
`input.press`, `input.drag`, `input.scroll`, `input.text`, `input.keys`, `nav.home`,
`apps.launch`, `apps.current`, `url.open`, `session.state`, `engine.stop`,
`engine.diag`, `engine.tune`.

## One app

Mode.app hosts the engine itself: the `ModeEngineHosted` bundle sits in
Mode.app/PlugIns. Xcode builds it as an app-hosted unit test, which iOS refuses
to let drive other apps ("Not authorized for performing UI testing actions").
To fix that, mark it in the `.xctestrun` as `IsUITestBundle = true`, with
`UITargetAppPath` set to Mode.app. testmanagerd then authorizes it.
`tools/run-engine.sh` does that.

Hosted this way, the engine kept serving with Uber in front for over a minute;
iOS did not suspend Mode. The separate `ModeEngine` runner target stays only
as a fallback.

## What it took on iOS 27

- **Foreground app.** There is no public API. The private path that works:
  `XCUIDevice.accessibilityInterface` (XCAXClient_iOS)
  → `activeApplications.first.processIdentifier`
  → `applicationProcessTracker` (XCUIApplicationMonitor)
  → `monitoredApplicationWithProcessIdentifier:`
  → `bundleID`.
  `+[XCAXClient_iOS sharedClient]` and `+[XCUIApplication applicationWithPID:]` are
  gone. `engine.diag` reports each step.
- **Speed.** XCTest waits for the app to go idle before and after every event,
  and Uber's live map never does. Setting `XCUIApplicationProcess.interactionOptions`
  to 3 (bit 1 skips the pre-event wait, bit 2 the post-event wait; confirmed
  by reading back `shouldSkipPre/PostEventQuiescence`) and turning off
  `idleAnimationWaitEnabled` made typing 3× faster and taps 2.0 s → 0.7 s. Gestures
  are anchored on the foreground app, so its settings apply. The agent verifies
  by reading the screen, not by XCTest's idle wait.
- **Spotlight is its own app**, `com.apple.campo`, not part of SpringBoard. Launching
  by name means: tap the Home Screen's "Search" button (or swipe down), wait for that
  app, type the name and Return, then wait for a third app to come forward.
- **Read whole snapshots.** Querying elements one by one is a round trip each,
  and the screen changes in between. `XCUIElement.snapshot()` gives the whole
  tree in one call (0.2–0.3 s on Uber).
- **XCTest failures** (say, typing with no field focused) are recorded, not
  thrown. `EngineTests.record(_:)` hands them to the op that caused them, so the
  client gets an error and the session keeps serving.
- No "Automation Running" banner appeared while the engine ran.

## Timings (Uber, iOS 27.2)

| Op | Time |
|---|---|
| `screen.text`, `tree` | 0.2–0.3 s |
| `screen.capture` | 0.2 s |
| `apps.current` | 0.01 s |
| `input.tap` | 0.7 s |
| `input.text`, 2 chars | 0.7 s |
| `apps.launch` by name (Spotlight) | about 6 s |
| Home → Uber → "Where to?" → "India Gate" → suggestions | 8.9 s |

## Try it

```sh
export MODE_ENGINE_TOKEN=$(uuidgen)
tools/run-engine.sh                 # prints MODE_ENGINE_URL
export MODE_ENGINE_URL=...
tools/engine.py text                # what is on screen
tools/spike_uber.py "India Gate"    # stops at the suggestions; requests nothing
tools/engine.py stop
```
