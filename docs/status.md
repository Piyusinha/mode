# Status

As of 2026-10-31.

## Phases

| Phase | What | State |
|---|---|---|
| 0 | Commit the XCUITest baseline, then remove it | ✅ Done |
| 1 | Prove CoreDevice remote control from a Mac: no overlay, taps land, Unicode | ✅ Done |
| 2 | `native/modecore`: our own Rust session on idevice, with a C API | ✅ Done |
| 3 | `shared/phone-coredevice`: the Kotlin `PhoneBackend` for iOS | ✅ Done |
| 4 | App integration: pairing, connecting from the phone, hands check, VPN, speed | 🟡 Mostly done |
| 5 | Docs | 🟡 This set |
| 6 | The agent: task in, AI picks each action, Mode executes | ⏳ Not started |
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

## Pending

### Phase 4, remaining
1. **Mount the developer disk image from the phone.** A reboot unmounts it,
   and until it is mounted again the services are gone. Today only the Mac can
   mount it. Then test: reboot with the cable unplugged, Mode still works.
2. **Stay alive in the background for long runs.** Tested only for about
   10 seconds while another app was in front. Needs background audio or
   location, then a 5-minute soak test.
3. **Mobile data.** Connecting through the VPN fails when only mobile data is
   on. Needs a test and a clear prompt (turn mobile data off for a moment while
   connecting).

### Phase 6, the agent
- The look → decide → act loop, with the AI's tools generated from `Op`.
- Ask the user before anything irreversible (pay, book, send, delete).
- Notifications when the task is done or the agent needs the user.
- Decide which model and whose API key.

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
