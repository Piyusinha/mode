# Status

As of 2026-11-01.

## Phases

| Phase | What | State |
|---|---|---|
| 0 | Commit the XCUITest baseline, then remove it | ✅ Done |
| 1 | Prove CoreDevice remote control from a Mac: no overlay, taps land, Unicode | ✅ Done |
| 2 | `native/modecore`: our own Rust session on idevice, with a C API | ✅ Done |
| 3 | `engine:phone-ios` (was `shared/phone-coredevice`): the Kotlin `PhoneBackend` for iOS | ✅ Done |
| 4 | App integration: pairing, connecting from the phone, hands check, VPN, speed, restart recovery, keep-awake, clean session end | 🟡 All but mobile data and the long soak |
| 5 | Docs | 🟡 Needs a pass for everything since the agent |
| 6 | The agent: task in, AI picks each action, Mode executes | ✅ Working on the phone; tuning continues |
| P1 | Production step 1: modular clean architecture (layers, one module per feature, StateFlow state, ModeGraph, SKIE) | ✅ Done, 74 tests; on-phone check pending (see below) |
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
| The "viewing and controlling" icon goes away when a session ends | ✅ Confirmed by the user |
| Agent: "Turn off dark mode" | ✅ 7 steps, 37 s, $0.0036 (Claude Haiku 5.5) |
| Agent: send a WhatsApp message, then read today's calendar | ✅ 11 steps, 62 s, $0.0065, no questions |

## Roadmap

In order. Items 1–7 are engineering; 8–10 are for launch; the free build stays last.

### Make the agent better
1. **Speed.** About a third of a run is waiting. Typing waits ~6 s, probably because a
   blinking cursor never gives two identical screenshots; each plan step takes a
   screenshot of an unchanged screen. Target: 30–40% faster runs.
2. **Pick the default model.** Compare tab on 3–4 real tasks across Claude Haiku 5.5,
   Qwen 3.8-27B, Qwen 3.7 Flash and GPT-5 nano; choose by cost per *finished* task.
3. **Start a task from anywhere.** Siri / Shortcuts / Action Button ("Hey Siri, Mode:
   turn off dark mode"), so a task starts from whatever is on screen. The agent already
   handles starting outside Mode.

### Finish the app
4. **Background soak.** 5 minutes with Mode in the background during a long task.
5. **Mobile data.** Connecting with only mobile data on still needs data switched off
   for a moment; test it and make the prompt clear, or find a way around it.
6. **UI/UX.** Agent screen with live steps, the plan, cost and stop; cleaner Settings
   and Logs; onboarding (API key, pairing check).
7. **Docs.** Bring architecture, decisions, flows and setup up to date.

### Production iOS app (designed 2026-11-01)
Step 1, the restructure, is done (D24–D26, `docs/architecture.md`). Next: step 2, the SwiftUI app.

Design canvas: https://claude.ai/artifact/WJsncuq9ZYwGkYMDBeuS4d (private to the owner).

- **Hands-free flow.** "Hey Siri, start Mode" → Mode listens on a Live Activity →
  the Dynamic Island shows each step while Mode works in other apps → the answer
  lands on the Lock Screen. To prove first on the phone: whether an App Intent can
  start listening and the Live Activity from the background (reports say a cold start
  is blocked; the fallback is Siri taking the task as dictated text).
- **App screens.** Chat (voice or typed tasks, plan cards, results, "Remembered"
  chips), Live run, Tasks (running, waiting for you, up next, every day), Memory
  (people, preferences, places; on the phone only; forget anytime).
- **Look.** Native Liquid Glass: SwiftUI for the iOS screens, Live Activity, Siri and
  speech; the agent, memory, tasks, model client and logs stay shared Kotlin so the
  Android app reuses them.

### Launch
8. **Mac setup app: one click.** A small Mac app that does the whole first-time setup
   in one click: installs Mode on the phone, makes and pushes the pairing file over
   USB, checks Developer Mode and the disk image, and says when the phone is ready.
   After that the Mac is not needed. Must work for both paid and free Apple accounts.
9. **Landing page.** What Mode does, the demo video, and the download for the Mac
   setup app.
10. **Demo video.** A short, polished screen recording of real runs from the phone
    (for example the WhatsApp + Calendar task), for the landing page and sharing.

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
| `f814d58` | Docs: restart recovery, keep-awake, reconnect; close the keyboard on Run |
| `554ee33` | Phase 6: the agent (OpenRouter), model comparison, settings, notifications |
| `9b5947f` | Debug logs for every run; end the session when idle |
| `2a42710` | Fix a launch crash in the event log; one-line tab labels |
| `7a031b3` | Pairing works without Xcode's pairing; a clear "unpaired" message |
| `c3e11ab` | Clear orphaned screen streams; let go after every hands check |
| `e74741a` | Let the session's goodbye reach the phone before stopping the VPN |
| `bd8ecb2` | Stop session button |
| `0ceca00` | End the session the way Device Hub does; the agent leaves Mode before looking |
| `6d60120` | Smarter agent: settle before acting, back, plan, iOS know-how; fix the Sonnet 400 |
| `c3cb155` | Agent: ask once and act on the answer; take the message text from the task |
