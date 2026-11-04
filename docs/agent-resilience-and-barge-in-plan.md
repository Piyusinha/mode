# Plan: an agent that fights to finish, app playbooks, and voice barge-in

## Context

**What prompted this.** The user asked for three things:
- (4) interrupting Mode while it speaks;
- (5) agent reliability;
- "advanced skills": Mode should *fight* to complete tasks instead of quitting.

The plan is based on all 11 runs on the iPhone (11/3, gpt-6.1-terra@medium hands, gpt-6.1-sol@high brain), a code map of every stop path, and current research (papers and open-source harnesses, summarised at the end).

**What the 11 runs show.** None of the four failures was a model that "couldn't". In order of impact:

| Run | What happened | Root cause |
|---|---|---|
| 19-48 IndiGo flight | Found 6E223 + PNR, typed the PNR into IndiGo's tracker, stopped at **step 30/30 one tap from "Search Flight"** | Hard 30-step cap; 11 steps lost to an `open_app` that reported "ok" while Mode stayed in front, and a boarding-pass detour |
| 20-06 Zomato order + pay | Scheduled 11:00 slot and added the item. Place Order tap did nothing, but harness said "**That confirms it**" (from a pre-existing "ADDED x" promo). The brain then forbade a retry. 11× back/escape loop, then step 30 | False commit confirmation; false duplicate block on "Continue"; no loop detector for repeated back; step cap |
| 19-44 "tomorrow's flight" | The FlightsMojo email with the booking was on screen at S4. A wrong "label→value" hint and `clear:true` not clearing Mail search ("4 November passNov"). Quit by choice with 8 steps left | Harness hints/input bugs; brain misread the booking date as travel date; a late-night "tomorrow" date error in the brief; give-up accepted after one alternative round |
| 20-12 Zomato "now" | Screen showed "CLOSED RIGHT NOW · Schedule · Tres Leches Creamery 10–15 min". Quit with 20 steps left, ignoring the brief's "ask first" | Give-up gate too weak; no rule forcing an option-question when the blocker is a choice |
| 19-22 hotels | Mode process died mid-run; run.json stuck "running", never resumed | No crash capture or resume |

**The two lessons.**
1. The harness, not the model, caused most failures.
2. Mode never tried a second strategy before quitting, except once when the user prompted it.

**Research that matches (sources at the end).**
- **VLAA-GUI:** give up only with evidence; "repetition alone does not establish infeasibility"; a 3-tier loop breaker. Without its completion gate, OSWorld drops about 3 points.
- **Mobile-Agent-E:** escalate after 2 verified failures; Tips and Shortcuts it learns itself. Termination errors fell from 52% to 12%.
- **Magentic-One:** a progress ledger and a stall counter that forces a replan.
- **minitap:** verified text input in code (−15 points without post-validation).
- **VeriSafe:** check irreversible actions with rules, not by asking an LLM to reflect.
- **MobileWorld:** asking the user is the alternative to giving up.
- **Barge-in:** one AVAudioEngine with voice processing; TTS through a player node so echo cancellation has the reference; gate on words, not loudness; ignore backchannels.

**Intended outcome.** Mode behaves like a persistent human assistant:
- it keeps a budget while it's making progress;
- it switches strategy when stuck;
- it asks with concrete options before quitting;
- it never claims a commit it can't see;
- it learns per-app playbooks ("when X happens, do Y");
- you can talk over it.

All of it stays generic (no per-app code), per the no-hard-coded-tasks rule.

---

## Part A — The agent fights to finish

Work goes in this order. Each item is a small tested commit, checked on the phone with the eval suite (A9).

### A1. Fix the harness bugs the runs exposed (first: cheapest, highest impact)

**False commit confirmation** (`ChangeLedger` / flash proof, `Agent.kt` ~480–536 and the `Changes.kt` classification)
- A change counts as confirmed only from text that **appeared after the tap** and wasn't on the before-screen.
- If the screen after a commit tap is the same page with the commit button still visible, mark the result "had no effect" and allow a retry.

**False duplicate block**
- Key the "already done" check on the tapped element's label plus the item, not on reason verbs.
- "Continue" or "View cart" after an add is navigation, not a repeat add.

**Label→value hint**
- Apply it only to form rows (label and value on the same row with a trailing value field).
- Never apply it to list rows (a mail row's date).
- Reuse the existing card grouping in `ContextBuilder`.

**Verified text input** (minitap)
- After `type_text`, re-read the focused field through OCR and report "expected X, field shows Y" in the step result.
- `clear:true` first taps a visible clear "ⓧ" control if OCR or the layout shows one, else select-all and delete. Then verify the field is empty before typing.

**open_app said ok, but Mode is still in front**
- After `open_app`, check the current app (`Op.CurrentApp`).
- If Mode is still in front: go Home, open the app again, check again. Report the result truthfully.
- Count this as "no progress" for the meter (A3).

**Date context**
- `TaskContext` "now" gains local time and a rule for late-night requests: before 05:00, "tomorrow" may mean today's date, so the brief states both readings and checks the evidence (a boarding pass or itinerary date).
- No task code: it's a date rule in the brief prompt (`Brain.kt` brief prompt plus `TaskRunner.today()`).

**Mode killed mid-run** (19-22)
- Each step's checkpoint goes to run.json: task, step, notes, plan, ledger.
- At launch, `TaskQueue.recover` marks it "interrupted" with the last step and offers **Continue** in the chat. Continue re-submits with the saved notes as context.
- Pull crash and jetsam reports from the phone (`devicectl … copy` of `/private/var/mobile/Library/Logs/CrashReporter`) and log the cause. Fix it if it's Mode.

### A2. A progress-based budget instead of a hard 30-step cap

- **Steps:** a base of 30 steps, plus 10 more each time progress is real (a new page reached, a plan step done, a verified change, new facts noted), up to **60**. The 15-minute limit stays.
- **Warnings:** at 70% of the budget, a one-line "BUDGET: N steps left — converge" note; at the end, one final "finish or ask" step.
- **Never stop one action from done:** if the last step typed into a field and the submit or search control is visible, grant 3 grace steps.
- **Where:** `AgentConfig.maxSteps` becomes `budget`; the loop in `Agent.kt:294` and the limit at :749; `ModeSettings.DEFAULT_MAX_STEPS`.

### A3. A recovery ladder with fixed thresholds (VLAA-GUI tiers, Magentic-One stall counter)

**Action-repeat detector.** Add it to `StuckDetector`: the same action kind and target ≥3 times in a window of 8, whether or not the screen signature changed. It catches the 11× back/escape loop. Back, escape and scroll count as repeats when the page cycles between two screens.

**The ladder:**

| Tier | Trigger | What happens |
|---|---|---|
| 1. Switch how you act | The same action twice with no change | Inject "try another way to do this step": search instead of browse, scroll the other way, dismiss the keyboard (`press_key escape` or tap outside), tap the visible "<" instead of the edge swipe. Automatic, no brain call. |
| 2. Switch strategy | The same screen 3× or a repeated action | Brain `decide` with a new **ROUTES TRIED** ledger, a list of `{route, result}` the agent keeps (Magentic-One task ledger). The brain must propose a route not in the ledger. |
| 3. Replan the subgoal | Two failed strategies | The brain rewrites the remaining plan steps (re-brief, keeping notes and routes tried). |
| 4. Hand off | Nothing left | `ask_user` with 2–3 concrete options, or `wait_for_user` for login, OTP or payment. |

**Remove the counter resets** that happen when a step-up is declined. Already partly done in commit 4912933; check and finish.

### A4. A give-up gate that needs evidence (VLAA-GUI STOP, MobileWorld)

`done(success=false)` gains required fields:
- `blocked_by`: one of `closed | unavailable | needs_info | needs_choice | login_or_payment | app_broken | not_possible`;
- `evidence`: on-screen text;
- `routes_tried`: a list.

**Harness rules:**

| `blocked_by` | Rule |
|---|---|
| `closed`, `unavailable`, `needs_choice`, `needs_info` | **Must** have called `ask_user` with options, unless full access is on and the brain marks one option clearly best, in which case it proceeds with that option. Otherwise the call is refused, and the brain's "options" verdict is turned into an `ask_user` automatically. |
| `login_or_payment` | Becomes `wait_for_user` (handoff, Operator takeover pattern), not a give-up. |
| Any other, with steps left | Refused while `routes_tried` has fewer than 2 distinct routes. |

- The existing single give-up check (`Agent.kt:486-503`) becomes this gate, with up to 2 rounds.
- Evidence must quote text that is present on the current screen or in the notes; the harness checks it against the OCR text.

### A5. Two-phase commit for irreversible actions (VeriSafe, vendor patterns)

The kinds are order, pay, book and send, already classified by `RiskGuard`.
- **Pre-commit:** before the commit tap, capture an evidence snapshot (merchant, total, items, time slot), from OCR into notes.
- **Commit:** one tap. After-shots at about 0.5 s and 2.5 s (catches toasts), compared with the before-screen.
- **Verify:**
  - **Success** needs new confirmation text (order ID, "placed", "confirmed", "booked", "on the way").
  - **Same screen** means no effect: one retry allowed.
  - **Unclear:** verify through the app's order history or Mail/Messages. Learned locations come from the playbook (A7), else "find Orders/Bookings/History in the profile or account tab".
  - Never re-commit while a verification is pending.
- **`done(success=true)`** for these kinds needs an evidence quote with an ID or status. It extends the existing verify-before-done (`Agent.kt:474-485`).

### A6. Notetaker and facts (Mobile-Agent-E)

- Every step, the hands model may set `facts: {key: value}` (flight number, PNR, date, total, ETA), merged into a TASK FACTS block shown each step and given to the brain.
- This replaces free-text notes for key data and stops "couldn't find the flight number" when it was seen earlier.
- `ContextBuilder` gets the block; the brain `decide` and brief see it.

### A7. App playbooks: "when X happens, do Y" (Mobile-Agent-E Tips/Shortcuts, AWM, Agent Skills)

**One store, three entry types per app,** extending `MemoryKind` (`feature/memory/.../domain/Memory.kt`):
- **Route:** the existing `Skill`, a path that worked.
- **Situation:** **new.**
  - `trigger`: exact on-screen labels in quotes, e.g. "CLOSED RIGHT NOW".
  - `response`: e.g. "offer Schedule for later or a nearby outlet that is open; ask which".
  - `success` / `fail` counts, and the app version seen.
- **Verify:** **new.** Where to check that a commit went through, e.g. "Profile › Your Orders".

**Retrieval.** `JustInTime` (`feature/memory/.../JustInTime.kt`) shows a Situation when **its quoted trigger is on the current screen**: an exact label match, which is more precise than today's 2-word overlap. At most 2 at a time, tagged `PLAYBOOK:` in the step context.

**Learning.**
- The `Reflector` adds Situations from failures and from recoveries that worked ("the agent was stuck at X, then Y worked").
- The `Teacher` turns user lessons into Situations ("when it says closed, schedule it").
- A Situation that fails twice is downgraded; one not seen in 60 days is archived.
- Learned entries show in **Memory → Apps** (editable, already exists).

**Generic obstacle playbook** in the system prompt: app-agnostic, not task code. A short table:
- pop-up, rating prompt, update prompt → dismiss;
- permission dialog → allow if the task needs it;
- login, OTP, Face ID → hand off;
- closed or out of stock → schedule, find an alternative, or ask;
- network error → wait, then retry;
- empty search → shorter query, then another app;
- payment sheet → hand off.

### A8. Missing capability: quick facts lookup (flight status, store hours)

- **New `lookup(question)` tool.** It asks the provider's built-in web search: the OpenAI Responses `web_search` tool on the ChatGPT plan, Claude's `web_search` server tool, or an OpenRouter `:online` model. It returns text plus sources, so it doesn't need to drive Safari for facts.
- **Spike first:** does the ChatGPT-plan token allow `web_search`? If not, fall back to driving Safari (today's behaviour).
- **Where:** a `LlmClient.lookup` capability; a tool in `AgentTools.kt`, offered only when supported.

### A9. Evaluation like a production harness team

**Golden-trace regression tests (offline, CI)**
- Turn the failing runs into fixtures: OCR plus layout per step from `steps.jsonl`, and a fake phone that replays the screens.
- Tests assert the harness rules: no false confirm (20-06 S16), no duplicate block (S14), label→value not on list rows (19-44 S9), the action-repeat detector fires on S19–29, the budget extends at step 30 on 19-48, and the give-up gate refuses 20-12 without `ask_user`.
- They run in `jvmTest` with no model calls: scripted models (the `ScriptedModel` pattern in `engine/agent` tests).

**On-phone eval suite (manual, ~12 tasks)**
- The tasks: Mail lookup, flight status, Zomato schedule, Rapido book, Settings, WhatsApp message, an order-history check, a hotel search, and one where a store is closed.
- Each task has success criteria. Run it from Settings → Developer (reuse `CompareRunner` and the Compare tab).
- Per task the report records success, steps, time, give-up category, false-done, false-give-up and recovery tiers used. The log lives in Documents, so it can be pulled.
- **Accept the A-series when** false-give-up = 0 on the suite, success at or above the current baseline + 30 points, and no task stops within 1 action of done.

**Traces.** Add `mode.agent.recovery` events (tier, trigger), `mode.agent.give_up` (`blocked_by`, routes count, accepted or refused), `mode.agent.commit` (kind, verified or no-effect or unclear) and `mode.agent.budget` (extended to N).

---

## Part B — Voice barge-in (talk over Mode)

### B1. One voice engine with echo cancellation (also removes the stuck-mic risk)

- **New `VoiceEngine.swift`:** one long-lived `AVAudioEngine` per talking session.
  - `inputNode.setVoiceProcessingEnabled(true)`, set while the engine is stopped.
  - Session: `.playAndRecord` + `.voiceChat` (+ `.allowBluetoothHFP`).
  - The ducking config is set to `.min`.
- **All TTS plays through an `AVAudioPlayerNode` on that engine,** so the echo canceller has the reference signal:
  - Apple voices: `AVSpeechSynthesizer.write(_:toBufferCallback:)` → buffers.
  - ElevenLabs/Fish: the mp3 decoded to PCM (`AVAudioFile` from temp data).
  - A 200 ms volume ramp at the start, for echo-canceller warm-up.
- **The listener reuses the same engine's input tap** for `LiveListener` (level, WAV, `AnalyzerFeed`), so no category switches happen between speaking and listening. That switching is the suspected cause of today's silent-mic bug.
- **Replaces:** `AVAudioPlayer`, `forSpeech` and the per-listen `AVAudioEngine`. `VoiceAudio.done()` is still called at session end (KeepAwake handover).

### B2. Detecting an interruption (word-gated, LiveKit and Vapi style)

- While Mode speaks, keep the tap and Apple's live transcription running.
- **Trigger:** speech above the adaptive floor for ≥250 ms **and** ≥2 recognised words that are **not** in the current TTS line (an echo guard on token overlap).
- **Backchannels** ("ok", "haan", "hmm", "yes", "mm-hmm") never interrupt.
- **On trigger:** fade TTS out over 80 ms, keep listening as a normal turn (TurnEnd rules), and log `mode.voice.barge_in` (phase, words, ms into the line).
- **False trigger** (no words within 1.5 s): resume the line from the next sentence.
- **Pure Kotlin logic,** so it's testable: `BargeIn.shouldInterrupt(heardWords, ttsLine, speechMs)` in `feature/settings/voice`, alongside `TurnEnd`.

### B3. Where barge-in applies, and what the words do

| When Mode speaks | Mode in front? | What the interruption does |
|---|---|---|
| Greeting | yes | Treated as the task (the greeting just stops) |
| AI acknowledgement (the task already sent) | yes, connecting | `runner.steer(words)` (existing `TaskRunner.steer`) |
| Spoken question | yes, stream paused (`duringVoice`) | `runner.answer(id, words)` |
| Final reply | yes (Mode comes to front) | A follow-up task in the same chat (`ConversationPresenter.send`) |
| Narration while the agent drives another app | **no**, stream on | **Spike first** (B4) |

- During a steer: `pause()` the `PauseGate` while listening, so the agent doesn't act before the words arrive; resume after.
- Mode says a short AI acknowledgement ("Okay, I'll use Flightradar").

### B4. Narration barge-in spike (D17 risk)

- **The question:** can the mic run while the screen stream is on and Mode is in the background? D17 says iOS won't *start* a stream while a foreground app records; this case is the other way round.
- **Test on the phone:** start a task, then during narration open the mic in VoiceEngine, and check that the stream frames keep arriving and the orange dot appears.
- **If it works:** enable barge-in during narration, with a hold on agent ops only after a confirmed trigger.
- **If not:** narration stays mic-off. Interrupting mid-task goes through the Live Activity / Dynamic Island **"Speak"** button (an App Intent that opens Mode listening, `steer`). That button is useful either way.
- Record the result as decision D-next in `docs/decisions.md`.

### B5. UI and tests

- **Talk screen:** a "Listening…" state appears when an interruption triggers (the orb's existing listening look); the TTS caption greys out where it was cut.
- **Kotlin tests:** `BargeIn` (echo guard, backchannels, thresholds) and routing (greeting → task, ack → steer, question → answer, reply → follow-up).
- **On the phone:**
  - interrupt the greeting, the acknowledgement, a question and the final reply;
  - check there's no self-interruption from Mode's own voice on speaker at full volume;
  - with AirPods;
  - in a noisy room.

---

## Order of work (each step a pushed commit, checked on the phone)

1. **A9 golden-trace fixtures + A1 bug fixes.** Tests first: they reproduce the 4 failures, then the fixes.
2. **A2 budget + A3 ladder** (action-repeat detector, ROUTES TRIED, tiers).
3. **A4 give-up gate + A5 two-phase commit.**
4. **A6 facts + A7 playbooks** (Situation/Verify entries, JustInTime triggers, Reflector/Teacher learning, the generic obstacle table).
5. **B1 VoiceEngine + B2 detector + B3 routing + B5.**
6. **B4 spike → the narration decision.**
7. **A8 lookup tool spike**, then the tool if the provider allows it.
8. **The on-phone eval suite run;** compare with the baseline; then the release work resumes.

**Docs:** `docs/harness.md` (recovery ladder, give-up gate, commit protocol, playbooks), `docs/decisions.md` (D-entries for budget, gate, VoiceEngine, the narration outcome) and `docs/status.md`.

## Critical files

**Agent**
- `engine/agent/.../agent/Agent.kt`: the loop, done handling, give-up check, hints, step-up.
- `StuckDetector.kt`, `ChangeLedger.kt`, `Changes.kt`, `ContextBuilder.kt`, `AgentTools.kt`.
- `Brain.kt`: brief date rule, decide with ROUTES TRIED, options verdict.
- `AgentConfig`.

**Settings, memory, tasks, LLM**
- `feature/settings/.../domain/ModeSettings.kt`: the budget defaults.
- `feature/memory/...`: `Memory.kt` (new kinds), `JustInTime.kt`, `MemoryRetrieval.kt`, `Reflector.kt`, `Teacher.kt`.
- `feature/tasks/.../TaskRunner.kt`: steer/answer routing for barge-in, checkpointing, recover.
- `TaskQueue.recover`.
- `engine/llm`: the `lookup` capability, `ResponsesClient` / `ClaudeOAuthClient` web search.

**Voice**
- New `iosApp/Mode/App/VoiceEngine.swift`.
- `LiveListener.swift`, `ModeApp.swift` (speak/play move to VoiceEngine).
- `core/platform/.../KeepAwake.kt` (`VoiceAudio`).
- New `feature/settings/.../voice/BargeIn.kt`.
- `ListenPresenter.kt`, `Talk.swift`.

**Telemetry and tests**
- `core/telemetry/.../Telemetry.kt`: new operations and events.
- Golden-trace fixtures in `engine/agent/src/commonTest` (generated from the 11 runs' `steps.jsonl`, with no screenshots committed and personal data scrubbed).

## Verification

- **CI:** `./gradlew jvmTest checkModuleRules`, golden-trace tests included. Every A-series rule has a failing-then-passing test built from a real run.
- **Phone, agent:** the eval suite before and after. False-give-up 0; no stop within 1 action of done; commits verified (order placed, or "no effect" detected and retried once); a playbook entry learned and reused on a second run (the "closed" situation).
- **Phone, voice:** the barge-in checklist in B5; the trace shows `barge_in` events, zero self-triggers over 10 spoken lines on speaker, and mic peak level > 0 on every listen (no stuck mic).
- **Traces pulled after each phone session** with the existing pull and decode script (`otlp_spans.py`).

## Defaults chosen (the user was asleep; change any in the morning)

- Budget: 30 base, up to **60** steps, still a 15-minute cap.
- When a store is closed or an item unavailable, **ask with options**. With full access and one option clearly best, take it.
- Lookup uses the provider's web search when the plan allows it. No new keys are needed.
- Narration barge-in depends on the B4 spike. The Live Activity "Speak" button is built either way.

## Sources (from the research pass)

- **Persistence and recovery:**
  - VLAA-GUI (arXiv 2604.21375)
  - minitap mobile-use (2602.07787)
  - Mobile-Agent-E (2501.11733)
  - Mobile-Agent-v3 (2508.15144)
  - AndroidReality (2608.07775)
  - Agent S3 / bBoN (2510.02250)
  - GTA1 (2507.05791)
  - Magentic-One (AutoGen docs)
  - Surfer-H (2506.02865)
  - MobileWorld (2512.19432)
- **Memory:** AWM (2409.07429), SkillWeaver (2504.07079), AppAgentX (2503.02268), AutoDroid, ICAL, Anthropic Agent Skills.
- **Commit safety:** VeriSafe Agent (2503.18492).
- **Vendor guidance:** Anthropic computer-use docs and "Effective harnesses for long-running agents"; OpenAI Operator/CUA takeover and confirmations; Gemini `require_confirmation`.
- **Voice:**
  - OpenAI Realtime `semantic_vad`
  - LiveKit adaptive interruption (86% precision / 100% recall at 500 ms) and turn detector
  - Pipecat Smart Turn v3
  - Vapi `stopSpeakingPlan`
  - Apple WWDC19/23 voice processing (`setVoiceProcessingEnabled`, player node for the echo-cancellation reference, ducking config, muted-talker events)
- Earlier internal audit: `reports/Reliable cheap iPhone agent harness.md` (most of its P0 items have already shipped).
