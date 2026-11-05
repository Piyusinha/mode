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

## Status, 5 November 2026

Built on branch `harness-fixes` (worktree `~/piyush/mode-harness`), each item a tested commit:
A1 (`ee60c63`, `9964428`, `e281984`), A10 (`ff16084`), A2+A3+A12 (`fbac612`), A4+A5 (`6ff7803`),
A6+A11 (`58b584a`), A13+C1–C4 (`8c7e92c`), A8 (`a5773d4`), B1–B3+B5 (`ace494b`). A9: the golden
tests exist; the on-phone suite is `docs/eval-suite.md` (runner UI pending). B4 (narration
barge-in) and the Live Activity "Speak" button are pending. Nothing has run on the phone yet.

## Review, 4 November 2026 (second research pass)

A second pass over the code and over what was published since the plan was written (StepReflect/Q-Reflector,
MobileUse, EvoSkill-GUI, PhoneHarness, AnTrap, "Do mobile agents need the phone screen?", AndroidDaily,
PersonalAlign, Ask-to-Remember, LGWM/gWorld). Three findings change the plan.

**1. The code already has more than the plan assumed.** These exist and should be extended, not rebuilt:

| Already in `Agent.kt` / `AgentTools.kt` | What it does today | Plan item it covers |
|---|---|---|
| `expect` on every action | The model says what it expects; the harness only shows it back next step ("YOU EXPECTED…") and never checks it | → A10 makes the harness check it |
| Verify-before-done (once per task) | A `done` with no evidence, or after an unchanged screen, is sent back once | A5's global check; extend for orders and payments |
| The give-up check (once per task) | The planning mind is asked for another route before a give-up | A4 formalises it with `blocked_by` |
| "When something is in the way" in the system prompt | Closed, missing info, empty search, pop-ups, login, unverified order | A7's generic obstacle table is **done** |
| `ask_user` with options and `recommended`; `wait_for_user` | Choice questions and hand-offs | A3 tier 4, A4 |
| Typed-text probe | After `type_text`, "the field shows X" or "does not show" | A1 verified input, half done |
| `ChangeLedger.looked` | Proof only from text that was **not** on the page before the tap; "no effect" when the page is 95% the same | A1's temporal rule is already there |
| `Op.OpenUrl` in `engine/ops` | A phone op to open a URL; **no tool exposes it** | → A11 |

**2. The false "order confirmed" root cause was wrong.** The ledger already ignores text that was on the page
before the tap. In run 20-06, both proofs *appeared after the tap* and still weren't proof: "You ordered 5 months
ago" (a history line in the sheet that opened) and "ADDED x" (a promo banner on the checkout page). The
`CONFIRMED` regex matches any past-tense verb. The fix is semantic, not temporal (A1, rewritten below):
proof must match the **kind** of change, past-tense history phrasing is excluded, and for a commit the commit
button must be gone.

**3. What the field learned since.** Numbers that set the priorities:

- **A per-action check is the single biggest gain.** MobileUse's action reflector (before/after diff, "did the
  action do what it should") adds **+5.7 points** on AndroidWorld; running it only on demand adds **+3.0**; the
  end-of-task check **+2.5**; the trajectory check **+0.9**. Q-Reflector reaches 82% accuracy on valid/invalid
  transitions by predicting pre- and post-conditions *before* looking at the after-screen. Mode has the
  `expect` field already: making the harness check it is cheap (OCR diff, no extra model call) → **A10**.
- **Deterministic channels beat screens.** PhoneHarness (route to a CLI/tool first, delegate bounded GUI
  sub-tasks) scores **75.0%** vs 62.1% for the best GUI-only agent, 97% where a non-GUI route exists. Claude
  Code over a CLI beats every GUI agent on AndroidWorld (71.8 vs 69.3) with **10.7 steps vs 18.6**, and GUI
  agents score **≤ 11% on cross-app tasks**. iOS has no shell, but it has URL schemes, universal links,
  Shortcuts/App Intents and system data Mode can read itself → **A11**.
- **Skills need a recovery section.** EvoSkill-GUI's multi-file skill (plan + backup + recover + failure
  examples) lifts MobileWorld **53 → 70%** (Qwen3.6-Plus). Ablations: in-run revision when the screen
  diverges **+6.7**, a critic that diagnoses in an isolated session **+8.6**, multi-file over single-file
  **+2.9** → **A13** upgrades A7.
- **Agents break on anomalies, not on tasks.** AnTrap's ten traps (pop-ups, a stale frame, a 20–50 px tap
  offset, a dropped command, a jump to Home, an injected BACK) drop strong models by 15–37 points. Two of them
  are exactly our failures: *state deadlock* (phone control silently dropping ops, 19-22) and *loop*
  (20-06) → **A12** plus fixtures in A9.
- **Success is a side effect, not an answer.** PhoneHarness Bench and AndroidDaily (GRADE, 87% agreement with
  humans) score the observable result (the order in history, the setting's value, the message in the thread)
  and negative constraints (no duplicate order). A9's criteria are rewritten that way.
- **A personal agent is a memory problem.** PersonalAlign (ACL 2026): clustering a user's past tasks by intent,
  actions, time and scenario into "prototypes" resolves vague requests ("order my usual") **+15.7 points** and
  halves false proactive suggestions. Ask-to-Remember: agents score **62 points below** an agent that knows the
  preference, and the bottleneck is *never asking*. Both are missing from the plan → **Part C**.
- **Not now:** learned next-screen predictors (LGWM, 17 ms/check, 0.987 AUC; gWorld) need training data we
  don't have. Mode's cheap substitute is A10. AndroidWorld is saturated (97%); MobileWorld, AndroidDaily and
  AnTrap are the sets that still discriminate, so A9 copies their shape.

---

## Part A — The agent fights to finish

Work goes in this order. Each item is a small tested commit, checked on the phone with the eval suite (A9).

### A1. Fix the harness bugs the runs exposed (first: cheapest, highest impact)

**False commit confirmation** (`Changes.kt` proof, `ChangeLedger`, `Agent.kt` flash reads). The temporal rule
exists; the proof is wrong *semantically*. Run 20-06: "You ordered 5 months ago" and "ADDED x" both appeared after
the tap and both matched the `CONFIRMED` regex.
- **Proof by kind.** Classify the change (add to cart, order/pay, book, send, save, delete) from the button's
  label and the reason, and accept only proof of that kind: an order needs "order placed", "confirmed",
  "preparing", "on the way", an order number or an ETA; an add needs "added", a cart count or "view cart"; a
  send needs the message in the thread or "sent".
- **History is not proof.** Exclude past-tense history phrasing ("ago", "last ordered", "previously", "you
  ordered", dates older than today) and promo tiles (short all-caps badges next to a price).
- **The button must go.** For order, pay, book and send: if the same commit button is still on screen after
  the settle, the result is "no effect" whatever text appeared. Then one retry is allowed (A5).
- **Golden test:** 20-06 S13 and S16 must yield "no proof"; a real "Order placed · #1234" must yield proof.

**False duplicate block**
- Key the "already done" check on the tapped element's label plus the item, not on reason verbs.
- "Continue", "View cart", "Checkout", "Next" after an add are navigation, never a repeat change: `isChange`
  returns false for them even if the reason says "order".
- **Golden test:** 20-06 S14 ("Continue >") goes through.

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
- **A cap per plan step too** (PhoneHarness's "bounded GUI delegation"): 12 steps on one plan step without
  reaching the next triggers tier 3 of the ladder (replan that step), not the global limit.
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

**Generic obstacle playbook**: **done** ("When something is in the way" in the system prompt covers closed,
missing info, empty search, pop-ups, login/OTP/Face ID, unverified orders). Add only two lines from AnTrap:
a network error → wait 5 s, then retry once; a permission sheet → allow if the task needs it, else dismiss.
The learned part of A7 is superseded by A13 (skill packages with a recovery section).

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

**Success is a side effect** (PhoneHarness Bench, AndroidDaily's GRADE). Each suite task states three tiers,
checkable from the trace and the final screens, never from the agent's summary:
- *obligations*: the observable result (the order in Orders, the message in the thread, the setting's value,
  the flight status spoken with the flight number);
- *output quality*: what the reply must contain (the time, the amount, the gate);
- *negative constraints*: what must never happen (a second order, a payment without `ask_user`, a message to
  the wrong person, giving up with a route untried).

**Anomaly fixtures** (AnTrap's ten traps, as offline tests and three on-phone injections): a pop-up mid-task; a
stale frame; a tap offset of 30 px; a dropped command for 3 steps (state deadlock); a jump to Home (context
disruption); an injected back every 2 steps (loop). Each has a required recovery (A12).

### A10. Check every action against what the model expected (Q-Reflector, MobileUse)

The largest measured single gain in the literature (+5.7 points; +3.0 more when run only on demand), and Mode
already has the input: the `expect` field on each action. Today it is only echoed back; the harness will check it.

- **Pre/post-conditions, cheaply.** `expect` becomes two short fields: `expect` (what should be true after:
  "the search results for IndiGo show", "the cart shows 1 item") and, for taps on fields, `focus` (which field
  should be focused). No extra model call: the harness compares with the after-screen's OCR and layout.
- **Signals the harness can read without a model:** did the screen change at all; did a quoted phrase from
  `expect` appear; is the keyboard up when a field was expected to focus; is the expected app in front
  (`Op.CurrentApp`); did a sheet or dialog appear instead (new large card over the old page).
- **Reflection on demand.** Only when a check fails (or on "(no valid action)") does the step result carry a
  reflection hint, Q-Reflector style: the action, what was expected, what the screen shows instead, and
  "do not repeat the same coordinates or text; the likely cause is X" (field not focused, tap landed on the
  label, a dialog is in the way, the app is not in front). A passing check costs nothing.
- **Feeds the ladder.** Two failed checks in a row on the same step count as a tier-1 trigger (A3).
- **Golden tests:** 19-44 S9 (label tapped, value field not focused), 20-06 S16 (Place Order, no transition),
  19-48 `open_app` with Mode still in front.

### A11. A direct channel before the screen (PhoneHarness, "Do mobile agents need the phone screen?")

Non-GUI routes are more reliable (75% vs 62%), shorter (10.7 vs 18.6 steps) and the only thing that works for
cross-app tasks. iOS has no shell, but it has these, all generic:

| Channel | Tool | Examples |
|---|---|---|
| URL schemes and universal links | `open_url` (exposes the existing `Op.OpenUrl`) | Open an app on the right page: a search, a product, a place, a flight; `shortcuts://run-shortcut?name=` |
| Shortcuts and App Intents | `run_shortcut(name, input)` | Anything the user (or Mode's setup) has as a Shortcut: toggles, sends, lookups |
| Mode's own data access | `read_calendar`, `read_contacts` (with the user's permission, read-only) | "Tomorrow's flight" from Calendar; a number from Contacts; no Mail API exists on iOS, so Mail stays GUI |
| Provider web search | `lookup` (A8) | Flight status, store hours, a fact |

- **Deterministic-first rule** in the brief: the planning mind lists, per plan step, the direct channel if one
  exists; the hands use the screen only for the app-specific part. A learned skill (A13) records which deep
  link worked for an app, so the second run skips the navigation.
- **Bounded delegation:** each GUI plan step has its own cap (A2) and returns a result the plan can check.
- **Spike first:** which of the user's apps accept URL schemes for search (most big Indian apps do); whether
  `shortcuts://` returns to Mode cleanly. Record the outcome as a decision.

### A12. Recover from anomalies, not just from being stuck (AnTrap)

Three traps are the phone's fault, not the model's, and the current `StuckDetector` treats them all as "stuck":

| Trap | Detect | Recover |
|---|---|---|
| **State deadlock** (ops silently dropped; 19-22) | The screenshot is byte-identical after two different actions | Not a model problem: `session.reconnect()`, re-settle, re-read; log `mode.agent.deadlock`; count no steps against the budget |
| **Context disruption** (a call, a notification, Home) | `Op.CurrentApp` is not the expected app and the model did not switch | Re-open the expected app (`open_app`, `fresh=false`) and continue the same plan step; no replan |
| **Stale frame** | Capture timestamp older than the last action | Discard and re-capture after settle; never act on a frame older than the last action |
| **Pop-up / sheet** | A new large card covers the page (layout diff) | Dismiss if it is not the task's (rating, update, promo); otherwise read it |
| **Loop** (BACK cycling two screens) | The action-repeat detector (A3) | Tier 1 → 2 of the ladder |

### A13. Skill packages with a recovery section (EvoSkill-GUI; replaces A7's Situation entries)

The learned entry per app and task kind is a small package, not a line:
- `route`: the steps that worked (today's `Skill`), with the deep link if one exists (A11);
- `backup`: another way to find each element (search instead of browse; the tab name instead of the icon);
- `recover`: "if X, then Y" rules from real failures ("if CLOSED RIGHT NOW: use Schedule, then ask which
  slot"); these are A7's Situations, kept with the route they belong to;
- `failures`: the last three failed runs' step summaries, for the next revision;
- `status`: `unverified` until one later run succeeds with it; `success`/`fail` counts; archived after 60 days.

**Revision, in isolation** (+8.6 in the ablation): after a failed run the `Reflector` diagnoses in a fresh
session with only the package and the run's steps (not the run's full context), then edits `route`, then
`backup`, then `recover`, at most two rounds. **In-run revision** (+6.7): when the screen diverges from the
route, the hands may mark the step "changed" with what they did instead; the Reflector merges it after a success.
**Retrieval** stays as planned: by task intent for the package; by exact on-screen trigger for `recover` rules
(at most 2 shown). **Memory → Apps** shows the package in plain language, editable.

---

## Part C — The personal side: knowing the user (PersonalAlign, Ask-to-Remember, PersonaAgent)

A personal assistant is a memory problem as much as an agent problem. Mode has `Fact`, `Skill` and `Tip`
memories, a `Teacher` (lessons from chat) and a `Reflector` (lessons from runs). What is missing is the use
of the user's own history to resolve vague requests and to ask the right question once.

### C1. Usuals: resolving "my usual", "the usual place", "like last time" (PersonalAlign's prototypes)

- **Task records.** Every finished task keeps `{intent, app, key choices (item, size, address, payment, time
  slot), weekday, hour, outcome}`; it already has most of this in `RunRepository`.
- **Prototypes.** Records that agree on intent and choices (same app, same items, similar wording) are
  clustered into a *usual*: "Swiggy · chocolate oats 100 g · pay from wallet · weekday mornings", with a count
  and the last date. Pure Kotlin: Jaccard on words + equality on choices; no model call. Updated after each task.
- **Use.** When a request is vague ("order my usual", "book my ride home", "the same as yesterday"), the brief
  gets the matching usual's choices as facts, so the hands do not ask. If two usuals match, `ask_user` with the
  two as options. Shown with the task's result ("Ordered your usual: …") so the user can correct it.
- **Golden test:** three past oats orders → "order my usual oats" resolves the item and the wallet without a
  question; two different usuals → one question with both.

### C2. Ask once, remember forever (Ask-to-Remember)

Agents sit 62 points below an agent that simply knows the preference, and the cause is never asking. Mode's
rule is no extra confirmation prompts, so the asking is light and never blocks:
- After a task in which a choice was made that will recur (size, address, payment method, a seat or slot
  preference, a language), the result card carries one tap: **"Use this next time?"**. Yes → a `Fact`.
- An answer the user gives to an `ask_user` with options is stored as a candidate; the second time the same
  answer is given it becomes a `Fact` without asking.
- Never ask the same thing twice; never ask for anything the task did not need.

### C3. The profile block (PersonaAgent)

A compact, budgeted profile goes into every brief: name and how to address them, home and work, people they
message most, payment preferences, dietary rules, language, and the top five usuals. Most of it exists as
`Fact`s shown with every task; this adds a budget (about 400 tokens), recency ordering, and the usuals.

### C4. Proactive, carefully (PersonalAlign's false-alarm result)

Routines ("every weekday 8:40, a cab to work") are detected from prototypes whose time and scenario are
stable (3+ records, same weekday/hour). Mode **suggests**, never acts: a notification "Book your usual cab?"
with one tap to run. Suggestions stop after two dismissals. Nothing is bought, sent or booked unasked.

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

1. **A9 golden-trace fixtures + A1 bug fixes** (with the corrected root cause). Tests first: they reproduce the 4 failures, then the fixes.
2. **A10 per-action check** (the biggest measured gain; cheap; the `expect` field exists).
3. **A2 budget + A3 ladder + A12 anomaly recovery** (deadlock, context disruption, stale frame share the detector work).
4. **A4 give-up gate + A5 two-phase commit.**
5. **A11 direct channels:** `open_url`, `run_shortcut`, calendar/contacts; the deterministic-first rule in the brief.
6. **A6 facts + A13 skill packages** (route/backup/recover/failures; isolated revision; Memory → Apps).
7. **C1 usuals + C2 ask-once + C3 profile** (C4 proactive last, behind a setting).
8. **B1 VoiceEngine + B2 detector + B3 routing + B5.**
9. **B4 spike → the narration decision.**
10. **A8 lookup tool spike**, then the tool if the provider allows it.
11. **The on-phone eval suite run** (side-effect criteria, anomaly injections); compare with the baseline; then the release work resumes.

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

**Second pass (4 Nov 2026):**
- Per-action checks: Q-Reflector / StepReflect (arXiv 2608.05587); MobileUse hierarchical reflection (2507.16853, ablations table).
- Direct channels: PhoneHarness (2606.14832); "Beyond the GUI Paradigm: Do Mobile Agents Need the Phone Screen?" (2606.19388).
- Skills with recovery: EvoSkill-GUI (2609.17653); K²-Agent know-what/know-how (2603.00676).
- Anomalies: AnTrap (2608.24099).
- Evaluation: AndroidDaily / GRADE (2605.27761); PhoneHarness Bench; MobileWorld (2512.19432).
- Personal: PersonalAlign / HIM-Agent (2601.09636, ACL 2026); Ask Now, Use Later / ATRBench (2605.28108); PersonaAgent (2506.06254); Graph-based personalized memory survey (2609.08599).
- Verifiers to revisit later: LGWM "Right screen, wrong transition" (2610.11942); gWorld (ICML 2026); Latent World Action Model (2610.14228).
