# Status

As of 2026-10-31.

## Phases

| Phase | What | State |
|---|---|---|
| 0 | Commit the XCUITest baseline, then remove it | ✅ Done |
| 1 | Prove CoreDevice remote control from a Mac: no overlay, taps land, Unicode | ✅ Done |
| 2 | `native/modecore`: our own Rust session on idevice, with a C API | ✅ Done |
| 3 | `shared/phone-coredevice`: the Kotlin `PhoneBackend` for iOS | ✅ Done |
| 4 | App integration: pairing, connecting from the phone, hands check, VPN, speed, restart recovery, keep-awake | 🟡 All but mobile data |
| 5 | Docs | 🟡 This set |
| 6 | The agent: task in, AI picks each action, Mode executes | 🟡 Built and unit-tested (12 tests); first real runs pending (needs the OpenRouter key) |
| Last | Free-account build tested with the LocalDevVPN app | ⏳ Deferred to the end at the user's request |

## Proven on the phone (iPhone 17 Pro, iOS 27.2)

| What | Result |
|---|---|
| No "Automation Running" overlay | ✅ Confirmed by watching the phone |
| Connect from inside Mode (paid build, own VPN) | ✅ 962 ms (first time 5976 ms, including the VPN permission prompt) |
| Look: screenshot + text read on the phone | ✅ 1.4 s for the first look, 0.8 s after |
| Open another app, tap text in it, come back to Mode | ✅ Settings → Wi-Fi → back |
| Touch after the session sat idle for 45 s | ✅ |
| Hindi text | ✅ Through the clipboard |
| Restart with the cable unplugged | ✅ The disk image stays mounted; connect 2340 ms |
| Disk image removed, Mode mounts it itself | ✅ 2.49 s, then driving (tested over USB with the bundled files) |
| Mode stays awake in the background, music keeps playing | ✅ Settings → Wi-Fi → back; look at 3.85 s, not 27 s |

## Pending

### Phase 4, remaining
1. **Mobile data.** Connecting through the VPN fails when only mobile data is
   on. Needs a test and a clear prompt (turn mobile data off for a moment while
   connecting).
2. **A longer soak.** Keep-awake is proven for a short run; a 5-minute run
   (as long as a real task) is still to do.

### Phase 6, the agent
- First real runs with the OpenRouter key, then the model comparison (Compare tab) to pick the default.
- Read the debug logs of those runs and tune the prompt.

### Last
- Free build (`tools/gen-project.sh free`) with LocalDevVPN, on the phone.

## Commit history

| Commit | What |
|---|---|
| `33a5c5b` | Baseline: the XCUITest engine as it stood (kept so it can be recovered) |
| `57ac25d` | Remove the XCUITest engine |
| `feee56b` | CoreDevice notes and Phase 1 results |
| `4f8b6dd` | `modecore`: native CoreDevice session in Rust on idevice |
| `83a6872` | Phase 2 timings and findings |
| `160ae6a` | `phone-coredevice`: the CoreDevice `PhoneBackend` |
| `e55f7f6` | Phase 4a: pairing setup and the on-phone hands check |
| `b863d51` | Phase 4b: loopback VPN route (paid built-in, free via LocalDevVPN) |
| `821a812` | Faster looks: one screenshot per look, decode once, half-size OCR |
| `b2742e2` | Docs |
| `fe503f8` | Mount the developer disk image from the phone after a restart |
| `9566e2c` | Keep Mode awake while it drives other apps; reconnect on drops |
| `63d7399` | Reconnect afresh after mounting the developer disk image |
