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
