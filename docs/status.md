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
| H1 | Harness step 1: the data layer (database, saved tasks and conversations, run index, typed settings, cost limit, live model catalogue) | ✅ Done, 91 tests |
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

## Mode v2: in now, and later one at a time (2026-11-05)

The full v2 UI was built and tried on the phone, then rolled back to v1 because it felt unstable (hold to talk
especially). All of it is saved: `git stash` "Mode v2 UI + unlock by voice (2026-11-05)…" and
`~/piyush/mode-backups/mode-v2-2026-11-05.patch`.

In now (on v1):
- The first run: Welcome → Connect your AI → First job (three small jobs, or type your own). The job runs in
  its chat like any task; the VPN and notifications are asked as it starts. Name, models, voice and
  dictation are set up later, after the first task.
- Unlock by voice: Settings → Unlock by voice (the guide and its check), tasks on a locked iPhone wait and
  start on unlock ("Unlock to start" on the Live Activity), Siri asks for Face ID, and the Mac Setup step.

Later, one at a time, each tested on the phone before the next:
1. Three tabs (Home, Tasks, Mo) with the talk bar (hold to talk, or type).
2. The loop sheet: say it → plan → working → your turn → done (Undo) / couldn't finish / stopped.
3. Home v2: needs you, running now, try these, done today, the trust offer.
4. Tasks v2: week card, Now / Scheduled / Done, weekly Recap with one idea, the new Repeat sheet.
5. Task page: "What I did" and "Next time" (make it a repeat, ask about it).
6. What went wrong v2: steps, where and why it stopped, last screen, "I'll finish it in <app>".
7. Mo tab pages: What Mo knows, Trust, Personality, Your AI (and the limit card), one Voice page, Health.
8. Plan before acting (waits for Go when it sends, orders or pays).
9. Trust levels per kind (reading, settings, messages, orders); paying always asks.
10. Approval cards with the real message or bill, and Edit before sending.
11. "Always <option>" on a choice, kept as a usual.
12. After the first task: offer to set up Mo's voice and dictation.
13. Next app update: the Home Screen widget and the Watch app.

## Roadmap

### Telemetry, diagnostics, and backup (started 2026-11-03)

- [ ] **Cross-platform OpenTelemetry:** shared Kotlin instrumentation, correlated
  traces/logs, offline persistence, and Rust metric aggregation. Reuse on Android;
  add platform adapters rather than duplicating flow instrumentation.
  **In progress:** shared SDK/OTLP persistence and task, connection, perception,
  model, voice, and reflection instrumentation are implemented. Common contract
  tests pass on JVM, iOS simulator, and Android library host target. Native detail,
  metric aggregation, physical-device validation, and overhead measurement remain.
- [ ] **Desktop diagnostics:** fetch phone and desktop traces, export reports,
  explain bottlenecks/failures with deterministic rules (no AI), and offer
  explicit repair actions.
- [ ] **Encrypted settings backup/restore:** include preferences and AI-provider
  credentials, protected by a separate backup password; exclude chats, memory,
  signing identities, and pairing records.

Implementation and acceptance criteria:
[OpenTelemetry, diagnostics, and backup plan](telemetry-diagnostics-backup-plan.md).
Current implementation: [Observability](observability.md). Desktop fetching,
deterministic analysis, and backup/restore are still pending.

### Focus now (set 2026-11-01)
In this order; everything else below waits until these are done.
1. ~~**H2, the context builder:**~~ done 2026-11-01: sectioned prompt with budgets, notes,
   verify before done, stuck check, time limit, daily cap, prompt caching
   (`docs/harness.md` §9).
2. ~~**H3, memory:**~~ done 2026-11-01: facts, skills, app tips, reflection,
   secret filter, "Remembered · Undo", `recall`, private tasks.
3. ~~**H4, queue and sources:**~~ done 2026-11-01: one task inbox and queue,
   recovery at launch, schedules with notifications.
4. ~~**SwiftUI app:**~~ built 2026-11-01:
   - `app:screens` presenters;
   - the `ModeDesignSystem` package with its literal check;
   - tabs, Home, Ask, Conversation, Live run, All screens, Tasks, Repeat, Memory,
     Settings and Onboarding.

   On-phone pass pending.
5. **Siri and the Live Activity:** "Hey Siri, start Mode", steps on the Dynamic Island,
   the answer on the Lock Screen.
6. **Voice transcription:** speak a task instead of typing it. On-device speech to
   text (Apple's `SpeechAnalyzer`, so it's free, private and works offline), a live
   transcript while you talk, then send. Per D17, the mic must stop before the agent
   drives. The Watch app reuses it through the watch's own dictation.
7. **Mac setup app:** one-click install and pairing (Launch item 8 below).
8. **The agent fights to finish** (built 2026-11-05 on branch `harness-fixes`, worktree
   `~/piyush/mode-harness`; JVM-tested, simulator-built, **not yet on the phone**):
   A1–A13 (the harness bugs from the 3 Nov runs, the per-action check, the step
   budget, the recovery ladder, anomaly recovery, the give-up gate, two-phase commit,
   facts, direct routes, lookup, skill packages), C1–C4 (usuals, ask-once, the profile
   budget, routines suggested behind a setting) and B1–B3 (talking over Mode).
   Next: merge into `main`, install, run `docs/eval-suite.md`. Still pending: talking
   over the narration (D17) and the Live Activity "Speak" button; Settings rows for
   `suggestRoutines` and `bargeIn`; the Compare-tab runner for the eval suite; the
   on-phone checks of `open_url` from the background, `read_calendar`/`find_contact`
   permissions, and `lookup` on the ChatGPT plan.

After that: Apple Watch app, landing page and demo video, then "Last".

### Earlier list
Items 1–7 are engineering; 8–10 are for launch; the free build stays last.

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

### Apple Watch (requested 2026-11-01)
- **Watch app (SwiftUI):** Ask Mode (tap or the Ultra's Action button) → speak → check → Send. The
  task goes to the iPhone over WatchConnectivity; Mode runs it like any other.
- **Progress on the wrist:** the iPhone Live Activity appears in the watch's Smart Stack
  by itself; the watch app also shows the step, a Stop button and the result.
- **One way in for every source:** Watch, Siri, Shortcuts and the app all hand tasks to one
  task inbox (a feature module), so adding a source doesn't touch the agent.
- **Two checks, done in the last steps once the app is complete** (see "Last"):
  waking from the background and controlling a locked iPhone.
- Designs: the "From your Apple Watch" row on the design canvas.

### Launch
7. **Start Mode from anywhere, locked or unlocked** (asked 2026-11-04).
   - **A "Talk to Mode" control** (iOS Control widget) for Control Center, the Lock Screen's
     two bottom buttons and the Action Button. One tap opens Mode listening; when the phone is
     locked, iOS asks for Face ID first. The existing "Hey Siri, start Mode" stays.
   - **Settings → Start Mode:** how to add it to Control Center, the Lock Screen, the Action
     Button and Back Tap, with the steps for each. Boards first.
   - **Phase 2, listening while locked:** an audio-recording App Intent starts the Live
     Activity and listens on the Lock Screen; the task waits for "Unlock to start" and runs
     on unlock. Prove on the phone first. Running the task while locked stays in "Last" 2.
8. **Mac setup app: one click.** A small Mac app that does the whole first-time setup
   in one click: installs Mode on the phone, makes and pushes the pairing file over
   USB, checks Developer Mode and the disk image, and says when the phone is ready.
   After that the Mac is not needed. Must work for both paid and free Apple accounts.
9. **Landing page.** What Mode does, the demo video, and the download for the Mac
   setup app.
10. **Demo video.** A short, polished screen recording of real runs from the phone
    (for example the WhatsApp + Calendar task), for the landing page and sharing.

### Last (once the app is complete)
1. **Woken by the watch (or Siri):** can Mode, started in the background, keep running a task?
   Its silent-audio keep-awake may not be allowed to start from the background.
2. **Locked iPhone:** does screen control work while the phone is locked? If not, the watch
   and Siri flows become "unlock your iPhone and Mode starts" (and the unlock idea is checked
   here too).
3. **Free build** (`tools/gen-project.sh free`) with LocalDevVPN, on the phone.

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
