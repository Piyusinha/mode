# Mode harness audit: why runs are unreliable, slow and indecisive, and the code changes that fix each failure

Sources are local, not web. Code paths are relative to `/Users/piyushsinha/piyush/mode/` (state at HEAD `fb11ebc`, 2026-11-01 19:39 IST). Run logs are under `/private/tmp/claude-501/-Users-piyushsinha-piyush-mode/c12fd8f1-a532-4e40-8a9f-a89fa990f41f/scratchpad/phone-test/logs/runs/<run>/steps.jsonl` (cited as `run <id> S<n>`), plus `logs/events.log`. Run timestamps are UTC and commit times are IST (+05:30). So 14-11-27Z ran on `fb11ebc`, 14-05-46Z on `c2328ab`, 14-01-05Z on `992100a`, 13-50-30Z and 13-30-58Z before `992100a` (no off-screen check), and 12-42-09Z before the ChangeLedger (`321cca5`, 12:56Z). Aggregate numbers come from a script over all 35 runs from 2026-11-01 (`scratchpad/audit/agg.py`).

## Q1. Per-run failure analysis: what the model saw, what it did, and why the harness let the loop continue

### Takeaway
None of the failed runs was a vision problem. Each time, the information was on screen. The failures came from a missing state signal (keyboard or focus), observations that were noisy or unstructured (OCR without grouping, placeholders), or a model/harness convention mismatch (Qwen coordinates). The harness's loop guards then failed to stop the loop, because they count only "screen unchanged after a phone action" by exact signature. They are also reset by escalations that never happen.

### Cited Findings

**Run 14-11-27 ("Add sushi in my car from Swiggy", Haiku; tapped "Focus search field" 6 times)**
- S2 tapped the Swiggy search bar. From S3 on, the screenshot shows the search field focused: a caret before the placeholder "Try 'Pizza'", the full QWERTY keyboard and a blue Search key — [run 14-11-27 step-3.jpg]. At S6 the iOS edit callout "Paste | AutoFill" also appears, because repeated taps on a focused field open it — [run 14-11-27 step-6.jpg, S6 OCR "Paste" (61,206), "AutoFill" (140,206)].
- What the model got as text at S3: 54 OCR boxes, 23 of them keyboard noise ("Q", "WER", "T", "Y", "S", "Z", "D"… "×", "123" and the suggestion bar "From MakeMyTrip"/"MMTDIWALI"). Nothing said that a field was focused or the keyboard was up — [run 14-11-27 S3 ask prompt]. The placeholder "Try 'Pizza'" (70,157) reads like an empty, unfocused field. It also rotates ("Try 'Biryani'" at S7), so the OCR text changes on its own — [run 14-11-27 S3/S7 acts].
- The model's reason was "Focus search field" at S3–S8, with expect "Search field active, keyboard shows" (S5–S7). The harness echoed "YOU EXPECTED … Does the screen below show that? If not, deal with it" — [ContextBuilder.kt:150-153; run 14-11-27 S6 prompt]. The OCR list cannot show "keyboard shows" except as stray letters.
- The prompt actively biases toward tapping: "Tap a text field before typing into it." — [Agent.kt:829], and type_text is described as "Type into the focused text field (tap the field first)" — [AgentTools.kt:108].
- Each tap was described back as `tap beside "Try 'Pizza'"` with outcome "ok → same page (only small parts changed)" — [run 14-11-27 S3–S8]. The harness marks this as "same page" but passes `changed = true` to the StuckDetector. `changed` is the raw signature comparison, and the "same page" branch does not set it false — [Agent.kt:264-274]. So `unchanged` never increments, no step-up happens and no stop happens — [StuckDetector.kt:45-51, 77]. The "visits" counter needs exactly equal signatures — [StuckDetector.kt:44] — and the rotating placeholder plus OCR jitter (y 152–156) defeat that.
- Each of these taps took 8.5–9.1 s of "act" time. Model time was about 2 s — [run 14-11-27 S3–S7 act ms 8812, 8463, 8548, 8544, 8587].
- The brain's brief for this run failed silently: Sonnet was paid $0.0149 and took 9.8 s (14:11:28.99 → 14:11:38.79), and the result was "no brief (anthropic/claude-sonnet-5.5): unknown" — [events.log line 1319; run 14-11-27 brief event]. Brain.brief sets `error = null` when the tool arguments exist but `parse()` rejects them — [Brain.kt:62-63, 121-124]. The same "unknown" failure appears at 13:59:33Z and 14:00:07Z — [events.log lines 1104, 1118]: 3 of the last 8 briefs.

**Run 14-01-05 (Swiggy, Haiku, code at 992100a: sorted by rating, then scrolled down and up)**
- Search and sort went right: S4 type "sushi", S5 return, S6 "Sort By", S7 "Rating (High to Low)" — [run 14-01-05 S4–S7].
- At S8 the screen showed an ad carousel (Foo 4.0, Shoyu 4.2), "WeFit – Protein Bowls 4.7 (186) AD" and part of "Matsuri – The Chancery 4.7 (106)" — [run 14-01-05 step-8.jpg]. After one scroll (S9), the top card's name ("Yuki") was hidden under the sticky sort/filter header. OCR returned "I UNI" (214,349) next to "4.6 (2.3K+)" — [run 14-01-05 step-9.jpg; S9 prompt]. The scroll moves 0.4 of the screen height — [AgentTools.kt:278] — so cards straddle the sticky header.
- The OCR list is in Vision's order, not reading order. Card fields come apart ("40% OFF", "+" and "UPTO ·80" interleaved with names), and "AD" labels are separate boxes — [run 14-01-05 S9 prompt].
- The choice was not obvious: Matsuri 4.7 with 106 ratings vs Yuki/Noodle Bar 4.6 with 2K+ ratings, plus a 4.7 ad. The brief's tie-break covers only exact ties ("more ratings first") — [run 14-01-05 brief]. The model went "Check top of sorted results" (up) / "Reviewing" (down) 10 times between S8 and S30, and re-planned 6 times — [run 14-01-05 S8–S30].
- A reversal was held once ("not done: you have scrolled back and forth", S12). At S13 the same "scroll up" went through. A Sonnet step-up at S14 ($0.0265) also just scrolled down — [run 14-01-05 S12–S14].
- 30 steps, 219 s wall, cart never reached — [run 14-01-05].

**Run 14-05-46 (Swiggy, Haiku, code at c2328ab with SEEN WHILE SCROLLING: 16 plan calls in a row)**
- S7–S8 sorted by rating correctly. S12–S14 scrolled down/up/down. Then S15–S30: 16 consecutive `plan` calls with the same 4-item plan, alternating "plan saved" / "plan (not saved)" — [run 14-05-46 S15–S30].
- The model had the data. "SEEN WHILE SCROLLING" listed "Yuki / 4.6 (2.3K+)", "Noodle Bar / 4.6 (2.0K+)", "Nasi and Mee 4.5 (7.5K+)" and "WeFit 4.7 (186)". The plan itself even said "Yuki 4.6 or Noodle Bar 4.6 seen" (S26) — [run 14-05-46 S24 prompt, S26 answer]. The list was a flat run of de-duplicated OCR lines, with no cards and the "AD" labels detached from their cards — [run 14-05-46 S24 prompt; Agent.kt:278-283, 317].
- Why plan alternated: `stuck.thought() >= 2` refuses the plan and calls `stuck.critical()` — [Agent.kt:414-416]. On the next look, `critical` returns `stepUp(null)`, which resets `plans = 0` and sets `steppedUp = true` — [StuckDetector.kt:57-60, 124-131]. The Agent then declines the step-up: after the S5 Sonnet step ($0.0252) and the brief, the budget check `maxCostUsd − cost > 3 × lastStrongCost` fails with the $0.10 default cap — [Agent.kt:297-298; ModeSettings.kt DEFAULT_MAX_COST_USD = 0.10]. Because `plans` was reset, the next plan is "saved", the one after is refused, and so on.
- Non-phone actions never count toward a stop. `changed` is null when the last step was not a phone action, so `unchanged` and `visits` don't move — [Agent.kt:264; StuckDetector.kt:41]. The run used all 30 steps — [run.json: "Reached the limit of 30 steps", $0.046].
- The only step-up (S5) went to a trivial action: Sonnet typed "sushi" for $0.0252. It was triggered by the plan refusal at S4 → critical(). That is 55% of the run's $0.046 cost, spent on a step Haiku could do — [run 14-05-46 S4–S5].

**Runs 13-50-30 and 13-30-58 (Qwen hands, qwen/qwen3.8-omni-flash: off-screen taps, stale page)**
- Qwen gives x coordinates on a 0–1000 scale even though the prompt says the screenshot is 460×1000 pixels and lists exact pixel centres. At S15 the OCR gave "Add Item | 7455" at (301,934). Qwen tapped (659,934), and 659/1000 × 460 = 303 — [run 13-50-30 S15 prompt and answer; step-15.jpg]. At S7 it meant "ADD" at (175,918) and sent x=389 (389/1000 × 460 = 179). The harness tapped at pixel 389, which is "ITEMS" — [run 13-50-30 S7 act `tap "ITEMS"`]. Later it also sent y=1868 and y=2039 on a 1000-px-tall image — [S17–S25].
- S2 sent `"x": [228, 269]` (a point as an array) and failed with "Element class JsonArray is not a JsonPrimitive" — [run 13-50-30 S2]. Pair parsing was added later — [AgentTools.kt:244-251].
- The ledger deadlocked. The S14 tap at x=659 px (576 pt on a 402 pt screen, the case named in AgentTools.kt:253) did nothing, but because its reason was "Adding…" it went into the ChangeLedger as a pending change — [Agent.kt:473, 531-536]. From S15 to S25, 9 of 11 attempts to tap "Add Item" were "held back: first check your change at step 14" — [run 13-50-30 S15–S25]. Earlier, Sonnet's correct "ADD" at S10 was also held back by the false change from S9 — [run 13-50-30 S10].
- Latency: Qwen median 6.8 s per decision, p90 21.3 s, max 33.6 s, against Haiku's 2.05 s median — [agg over 48 Qwen answers]. In 13-50-30 the model took 239 s of a 320 s wall time.
- 13-30-58: the brief was off, and the planning note copied stale memory ("Prior Swiggy knowledge: … KOKO 4.4 … Daily Sushi 4.4") from the app tips into the task — [run 13-30-58 S1 note]. Swiggy reopened on an old restaurant page (Huna'an). Qwen used `back` (an edge swipe) twice, taking 8–25 s per decision — [run 13-30-58 S3–S4].

**Run 12-42-09 (MyFitnessPal, before the ChangeLedger: 5 repeated adds)**
- "Food logged!" flashed after taps at S9, S17, S21, S24 and S29. All five were taps at (410, 340/353), the + icon, which the history describes only as `tap (410, 340)`. The icon has no OCR text, or OCR read it as "十" — [run 12-42-09 S9–S29; Changes.kt:27-28].
- The model gave reasons like "Check the 100 g item is logged" and "Verify: check Dinner log" while tapping the add button (S18, S21) — [run 12-42-09]. It also re-planned 8 times — [run 12-42-09 S2, S7, S8, S10, S11, S19, S20, S25, S26].
- This run is where the ChangeLedger, `ADD_IS_NOT_A_CHECK` and `flashed()` came from — [ChangeLedger.kt:9-10; Agent.kt:474-491, 529-536; commit 321cca5].

**Run 13-22-48 (MyFitnessPal "Then you need to add in today right?": scroll loop for Snacks)**
- At S5 the model noted "Breakfast … includes Beast life isorich 137 cal" — [run 13-22-48 S5 note]. Even so, it scrolled down/up/down/up/down/up/down from S7 to S12 "Check Snacks section". Sonnet stepped up twice (S10 scroll; S13 done) at $0.008 and $0.0094 — [run 13-22-48].
- A memory fact in ABOUT THE USER said "It is usually already in the Snacks section of the diary" — [run 13-22-48 S1 prompt]. That was wrong for today and pushed the model to keep looking for Snacks.
- Each scroll down ended with "Dinner" at the very bottom, under an ad block. "Snacks" was further down and never appeared, so the model reversed instead of continuing — [run 13-22-48 S8/S9 prompts]. An earlier recall tip said "scrolling back and forth wastes steps" — [run 13-22-48 S1 prompt] — and it had no effect.
- S2's plan was refused and set critical, so S3's Sonnet step was spent on `open_app MyFitnessPal` ($0.0092) — [run 13-22-48 S2–S3].

**Run 13-04-40 (Zomato, succeeded in 15 steps, 135 s)**
- Of the 15 steps, 6 were plan or recall (S2, S6, S7, S9, S10, S12, S13). Recall returned an unrelated Swish skill twice (S9, S12) — [run 13-04-40]. Three Sonnet step-ups (S8, S11, S14, about $0.008 each) did the real work: opening YUKI, ADD, "Add item". Haiku did navigation only — [run 13-04-40].
- Time: act 67 s, of which taps took 8–12 s each (S3 12.2 s, S5 12.1 s). LLM 44 s, look 24 s — [run 13-04-40].

### Inferences
- The common pattern: each failure shows the model what it needs in the screenshot. The text channel, which the model relies on heavily (rules say "use its listed centre"), loses that information: focus/keyboard, card grouping, ad association, and what lies under sticky headers.
- The harness's guards react to symptoms. They key on exact OCR-signature equality and on English verbs in the model's `reason`, so OCR jitter, animations and wording defeat them.
- Escalation, the main recovery mechanism, is budget-capped to about one Sonnet step per task with the $0.10 default. The many `critical()` triggers spend that single step early, on trivial moments (typing "sushi", open_app). Nothing is left for the real decision.

### Gaps
- Why Sonnet's brief failed to parse in 3 of 8 runs: the raw tool arguments are not logged when `parse()` returns null [Brain.kt:62-63].
- Runs 14-11-27 and 13-30-58 end with "running" and no end event (probably stopped by the user or the app). Their final outcome is not recorded.
- The `decide` tool (fb11ebc) has not yet been exercised in any logged run, so its effectiveness is unmeasured.

## Q2. Prompt and context: size, what dominates, what is redundant, what is missing

### Takeaway
Each step sends about 9.6k prompt tokens (median; max 13.3k). About 8–10.5k of that is a cached fixed prefix: a 9.7k-character rule prompt, 16 tool schemas and memory. Only about 1.7k tokens (median) change per step. The per-step part is dominated by unsorted OCR lines and repeated refusal messages. The state the model most needs is absent: a focused field or keyboard, an open sheet, the end of a list, a structured element list, and a diff against the last step.

### Cited Findings
- Prompt tokens per decision: median 9,616, max 13,279. Uncached share median 1,725 — [agg over 368 answers]. The Swiggy runs cache a 10,295–10,552-token prefix — [run 14-11-27 S1 cache_write 10,295; run 14-05-46 cached 10,552].
- The SYSTEM_PROMPT is 9,747 characters / 1,798 words — [Agent.kt:792-925, measured]. Every phone tool also carries `reason`, `note`, `item` and `expect` params — [AgentTools.kt:191-198]. That makes 16 tools, with plan/decide/recall/ask_user/done text descriptions — [AgentTools.kt:86-190].
- The stable block holds ABOUT THE USER (up to 25 facts), WHAT WORKED BEFORE, app tips (up to 10), RECENT TASKS (5), EARLIER TURNS (3), the TASK and THE BRIEF — [ContextBuilder.kt:80-118]. In run 14-11-27 the Swiggy task's prompt carried two WhatsApp/Sonali facts, two MyFitnessPal facts and five previous failed Swiggy tasks ("→ could not finish" ×5) — [run 14-11-27 S1 prompt].
- Memory carried stale or wrong specifics: "KOKO 4.4 (350 ratings), Daily Sushi 4.4" (tips), and "usually already in the Snacks section" — [run 14-11-27 S1; run 13-22-48 S1]. Qwen copied the stale ratings into a note as fact — [run 13-30-58 S1].
- Per-step block: STEP n, DONE SO FAR, NOTES, the last 12 history lines word for word with older ones summarised, the last app, SEEN WHILE SCROLLING (up to 120 lines), YOU EXPECTED, YOUR PLAN, then up to 80 OCR boxes as `"text" at (x, y)` — [ContextBuilder.kt:121-168; Agent.kt:46, 774].
- OCR boxes are listed in Vision's output order and capped at 80 with no sorting — [ContextBuilder.kt:166; VisionTextReader.kt:35-50].
- Across 339 screen-bearing steps, the average was 32.5 OCR boxes. In 40 steps the keyboard was up, and those carried on average 20.6 keyboard-key boxes each — [agg].
- Refusal messages enter the history and repeat for up to 12 steps. `checkFirst` is about 400 characters — [Agent.kt:665-669]. In run 13-50-30 the per-step text grew from 6.4k to 10.8k characters during the held-back streak (S15–S22) — [run 13-50-30 promptchars].
- "App you last opened: bundl.swiggy" shows a bundle id rather than an app name — [run 14-11-27 S3 prompt; Agent.kt:520].
- There is no accessibility tree on this path. `screen.text` is OCR only, and `Op.Tree` / `Op.CurrentApp` are Unsupported — [CoreDeviceBackend.kt:30-31, 102-103].
- Cost per step: Haiku with cache about $0.0003–0.0004. A Sonnet step-up is sent deliberately uncached [Agent.kt:324-325] and costs $0.008–0.0265, which is 25–80× a Haiku step. The brief costs about $0.015 — [runs 14-05-46, 14-01-05, 13-22-48, 13-04-40].

### Inferences
- Missing state that would have prevented the observed loops:
  - KEYBOARD UP / field focused (14-11-27).
  - A sheet or modal in front (13-50-30).
  - Reached end of list / scroll did nothing (13-22-48).
  - Cards with their AD flag and rating as one line (14-01-05, 14-05-46).
  - "New since last step" text (proof of add without the flash).
- Removing keyboard boxes, status-bar text and duplicate per-line fragments, and merging same-row boxes into one line, would plausibly shrink the screen list by 30–60% on keyboard screens. It would also make SEEN WHILE SCROLLING usable for comparison.
- Filtering memory by the app or intent of the task (no Sonali facts for Swiggy, no "could not finish" history) would cut prefix tokens and remove harmful priors. The 13-22-48 Snacks misdirection and the 13-30-58 stale-ratings note both came from memory.
- The rule prompt has accumulated incident-specific rules ("Dates mean today…", MyFitnessPal-style amount rules, Markdown reply format). Moving the reply-format rules (lines 907-921) to the `done` tool description and trimming the rest would lower the per-step cost on cache misses and for models without caching (Qwen showed `cached=None` on some steps).

### Gaps
- The exact token split between system text, tool schemas and the image cannot be read from the logs. Only totals and the cached part are logged. A character estimate (about 2.4k tokens of rules, about 1.8k of tool source) does not account for all of the 10.3k cached prefix. Tool-use overhead added by the provider is probably part of the rest, but this is unverified.

## Q3. Speed: where per-step time goes

### Takeaway
"Act" time, which is mostly `settle()`, is the biggest cost: about 1,205 s against 1,166 s of model time and 339 s of looking, across all runs. A tap's median act time is 4.1 s (p90 8.7 s), although the touch itself takes about 50 ms. `settle()` compares byte-identical 160-px JPEGs, each made from a fresh full-resolution PNG capture, so any caret blink, rotating placeholder or animated banner keeps it looping to its cap of 7 captures.

### Cited Findings
- Totals across 35 runs: look 339 s, LLM 1,166 s, act 1,205 s. Act includes about 27 s of one ask_user wait — [agg].
- Medians: look 664 ms (p90 1,586). Haiku 2,054 ms (p90 2,632). Sonnet 3,991 ms. Qwen 6,772 ms (p90 21,323, max 33,622) — [agg].
- Act medians: tap 4,110 ms (p90 8,692, max 12,177), scroll 4,327, open_app 4,457, type 3,720 — [agg]. The same tap was 8.5–9.1 s on the focused Swiggy search field (caret blinking, placeholder rotating) and 1.8–2.6 s on static screens — [run 14-11-27 S3–S7; run 14-05-46 S7 2,607 ms; run 14-01-05 S6 1,794 ms].
- `settle()` waits 400 ms, then takes a tiny shot and up to 6 more, 150 ms apart, until two are byte-identical. That is 2 to 7 captures — [Agent.kt:594-604; settleMs 400 at Agent.kt:42].
- Each tiny shot is `Op.ScreenCapture(maxSize = 160)` — [Agent.kt:607-610]. In the backend that is a full-resolution PNG screenshot shrunk to JPEG — [CoreDeviceBackend.kt:138-155]. Each screenshot opens a new ScreenCapture service connection, because "the capture service drops its connection after a shot or two, so each shot reconnects" — [session.rs:283-295].
- After settle, `look()` takes yet another full capture, then OCR (reusing that capture) — [Agent.kt:632-642; CoreDeviceBackend.kt:174-180]. That is 3 to 8 full PNG captures per step.
- For taps classified as changes, `flashed()` adds 400 + 800 ms of delays and two more screenshot+OCR passes — [Agent.kt:648-662, 781].
- OCR uses Vision `.accurate` with language correction on a 1,400-px image — [VisionTextReader.kt:24-25, 67-71].
- The session keeps a live screen video stream open (iOS 27 requires it for HID input). Its RTP packets are read and discarded — [session.rs:1-5, 472-473, 551-588].
- The brief adds 7.5–9.8 s before the first action — [run 14-05-46: 14:05:46.0 → 14:05:53.5; run 14-01-05: 14:01:05.9 → 14:01:15.5; run 14-11-27: 9.8 s, with no usable brief].
- open_app acts take 3.2–11.9 s — [run 13-04-40 S1 11,862 ms; run 14-11-27 S1 6,753 ms].
- Seconds per step: 7.3 s in run 14-01-05 (219 s / 30); 4.9 s in 14-05-46 (148 s / 30, where 16 steps were plan calls with no act); 10.7 s in 13-04-40 (135 s / 15).

### Inferences
- Estimate: each settle iteration costs about 1 s ((8.5 s − 0.4 s − 0.9 s of delays) / 7 captures in 14-11-27). With a tolerant comparison (mean absolute pixel difference under a threshold, ignoring the status bar and the caret), a ~1.2–1.5 s cap, and reuse of the final full-resolution frame as the `look()` image, a typical tap step would drop from about 4–9 s to about 1.5–2.5 s. That would roughly halve wall time per step for Haiku runs.
- Decoding frames from the already-open video stream (session.rs) would make stillness checks nearly free. That needs an H.264 decoder in the native layer, so it is a medium-effort change.
- Running the brief concurrently with the first look (or skipping it for one-app, single-intent tasks) would save about 8–10 s per task.
- Qwen's latency (p90 21 s) makes it unsuitable as the "hands" model regardless of its accuracy.

### Gaps
- No per-capture timing is logged inside `settle()`, so the per-capture cost (about 1 s) is inferred, not measured. Adding a trace of how many settle iterations ran and the ms of each would confirm it.

## Q4. Action space: is it right, and how are taps described back?

### Takeaway
The action space is raw screenshot-pixel coordinates with OCR centres as hints. There is no element targeting, no "type into this field" action and no focus feedback. That puts coordinate conventions and focus management on the model, and it makes tap descriptions ambiguous ("tap beside …", "tap (410, 340)").

### Cited Findings
- tap and long_press take x and y in screenshot pixels. scroll takes a direction (0.4 × screen height, or 0.6 × width horizontally). type_text types into "the focused text field". press_key takes named keys. back is an edge-swipe drag. open_app has a `fresh` flag — [AgentTools.kt:86-138, 264-297].
- Out-of-range coordinates are rejected with a message giving the range (added in 992100a) — [AgentTools.kt:252-260]. This does not catch 0–1000 normalized x values that fall inside 0–460, which were most of Qwen's taps — [run 13-50-30 S7].
- Typing goes over HID key codes for ASCII, otherwise through the pasteboard plus Cmd+V — [CoreDeviceBackend.kt:82-85; session.rs:249-282]. Neither path focuses a field.
- How taps are described: the nearest OCR text within 60 px; or, for short or no labels, the text to the left on the same row ("beside"); otherwise raw pixels — [Agent.kt:710-744, NEAR_PX 60]. Observed descriptions:
  - `tap beside "Try 'Pizza'"` for a tap on the search field itself [14-11-27].
  - `tap "Q" beside "123"` for the keyboard's Search key [14-05-46 S6].
  - `tap (410, 340)` for the MyFitnessPal + icon [12-42-09].
  - `tap beside "gourmet · one"` [13-50-30 S6].
- After each action the history gets "ok → screen changed / same page (only small parts changed) / screen did NOT change" — [Agent.kt:264-274].
- Scroll at 0.4 × screen height left the top card's name under Swiggy's sticky header — [run 14-01-05 step-9.jpg]. In MyFitnessPal it never reached "Snacks" before the model reversed — [run 13-22-48 S8/S9].

### Inferences
Recommended action-space changes. All are generic hands that encode nothing about any app, consistent with the "no hard-coded tasks" rule.
- **Element-indexed targeting (set-of-marks).** Number the grouped OCR rows and elements in the text list (`[17] "Add Item | ₹455" (301,934)`) and let `tap` take `id` or x/y. This removes coordinate-convention errors (Qwen), makes tap descriptions exact ("tap [17] Add Item | ₹455"), and gives the ChangeLedger a stable target key.
- **`type_text` with an optional target** (`id` or x/y) that taps, waits about 300 ms and types, plus an optional `submit: true` that presses return. With a keyboard-up detector, a type_text sent while the keyboard is up types directly, and one sent while it is down auto-taps the given target. This merges the "tap field → type → return" sequence of 3 steps into 1.
- **Larger scrolls with overlap and an end-of-list signal.** Use about 0.6–0.7 of the height. When a scroll leaves the page text unchanged, tell the model "reached the end of this list".
- **Per-model coordinate convention.** If raw coordinates stay, add a model-family flag (for example "normalized-1000") in AgentConfig/LlmClient that rescales before `toOp`. This applies to Qwen-family models, whose x values matched 0–1000 normalization in every checked case [run 13-50-30 S7, S14–S15].

### Gaps
- Whether Haiku and Sonnet would reliably use element ids rather than coordinates is untested here. It needs a bake-off run.

## Q5. Loop and stuck handling, and the recent heuristic guards: which work, which conflict

### Takeaway
The guards were each added after a specific incident, and they now interact badly. StuckDetector counts only exact-signature "unchanged" screens after phone actions. Its step-up verdict resets its own counters even when the Agent declines to step up. `critical()` is fired from about 7 places and uses up the one affordable strong step on trivial moments. The ChangeLedger prevents duplicate adds but deadlocks when a change-tap missed. Refused actions (plan, held back, recall, not done) never count toward stopping the run.

### Cited Findings
Inventory of the guards, with evidence for each:
1. **Change annotation and BACK_DID_NOTHING** — [Agent.kt:264-274]. "Same page after tap" does not feed `unchanged`, so the 6 identical taps in 14-11-27 went undetected.
2. **StuckDetector**: step up after 2 unchanged; stop at 4 unchanged after a step-up; warn at 4 visits and stop at 6 in a 12-step window; reversals ≥ 3; plans ≥ 3; waits ≥ 3; bad replies ≥ 2; critical — [StuckDetector.kt:39-80, 134-142]. `stepUp()` resets `sinceStepUp`, `badReplies`, `waits` and `plans`, and sets `steppedUp = true` unconditionally — [StuckDetector.kt:124-131]. The Agent separately caps step-ups at 3 and by budget — [Agent.kt:297-299]. Result: the alternating plan loop in 14-05-46 (S15–S30).
3. **Plan refusal** after a previous plan — [Agent.kt:411-421]. 71 of 368 decisions (19%) were `plan`, and 16 of those were refused — [agg]. With a brief, plan is no longer offered (fb11ebc) — [AgentTools.kt:203-210]. But briefs failed in 3 of the last 8 runs, which brings plan back — [events.log 1104, 1118, 1319].
4. **ChangeLedger and Changes**:
   - Classification is by English-verb regex on `reason`, or a short label/plus sign — [Changes.kt:10-40].
   - A confirmed change is refused; the next change is held until a pending one is checked — [Agent.kt:480-492].
   - Proof comes from flash reads — [Agent.kt:531-536].
   - It worked in 13-04-40 (proof shown, done at S15). It deadlocked in 13-50-30, with 9 held-back attempts after a missed tap (S15–S25), and it blocked Sonnet's correct ADD at S10.
   - False-positive classification: navigation taps whose reason contains "add"/"log" ("Open Dinner log to add food" on "Log") register as changes — [run 12-42-09 S4 outcome "This changed something"; Changes.kt:36-37].
5. **ADD_IS_NOT_A_CHECK** (an add button tapped with a "checking" reason) — [Agent.kt:474-478]. It targets the 12-42-09 S18/S21 behaviour.
6. **Scroll reversal hold + askBrain** — [Agent.kt:493-502; StuckDetector.kt:102-107]. In 14-01-05 it held once (S12) and the model reversed again the very next step (S13).
7. **Same-way scroll hint** ("use Sort, Filter or Search") — [Agent.kt:537-538].
8. **ask_user refusal after an answer; recall de-duplication; wait-count message; done verification once** — [Agent.kt:355-457]. These are cheap and local. No evidence that they misfire.
9. **critical() sources**: pending ledger first look; ask_user after any ledger entry; plan refusal; ADD_IS_NOT_A_CHECK; confirmed repeat; held back — [Agent.kt:287, 393, 415, 476, 482, 488]. Observed step-ups spent on trivial moments:
   - Sonnet typing "sushi" ($0.0252) [14-05-46 S5]
   - open_app ($0.0092) [13-22-48 S3]
   - a wrong tap on a promo ($0.0080) [13-04-40 S8]
10. **STEP_ANSWER_MS = 25 s timeout** — [Agent.kt:768]. Justified by Qwen's p90 of 21 s and max of 33.6 s.
11. **RiskGuard confirmation** before pay/book/send/order/confirm/remove/… — [Agent.kt:503-508, 929-940]. None of the audited runs reached it.

### Inferences
Simplify to a few principled mechanisms.
- **One progress meter.** A step makes progress only if a phone action changed the page or the ledger, judged by page similarity rather than exact signature. Count every non-progress step: refused, plan, recall, held, same-page tap, unchanged. At N = 3, escalate (to the brain's `decide` or Sonnet). At N = 6, stop with an honest reason. That replaces unchanged, sinceStepUp, badReplies, waits, plans and visits.
- **Let the Agent tell the detector whether a step-up actually happened.** For example `stuck.looked(...)` returns a proposal and the Agent calls `stuck.steppedUp()` only when it really used the strong model. Then declined escalations don't reset counters.
- **Reserve the strong model or brain for decisions.** Remove `critical()` from plan refusal and the first pending-ledger look. Trigger escalation on (a) a reversal or a "check top of list" move after sorting or scrolling, which is a choice moment, and (b) the progress meter. Budget the brief separately so it doesn't block step-ups: today `brief cost + one Sonnet step` already exceeds the `3× strong cost` headroom under the $0.10 cap.
- **ChangeLedger fixes.**
  - If a change-tap leaves the page the same (similarity ≥ 0.85, no new text), mark the entry "had no effect" and allow a retry instead of holding.
  - Classify changes by label (Add/+/Send…) rather than verbs in the reason.
  - Key entries by element id once ids exist.
- **Remove the plan tool entirely.** Keep the brief's steps as the plan, or make it an optional `plan` string on the first action. This eliminates 19% of decisions.

### Gaps
- Whether the newer guards (decide-on-reversal in fb11ebc) fix the Swiggy choice loop is unmeasured: no post-fb11ebc run reached the list stage.

## Q6. Prioritized fixes with expected effect

### Takeaway
The best-value changes, in order:
1. Detect the keyboard/focus and make `type_text` focus by itself.
2. Fix `settle()`.
3. Unify loop accounting, so that refused or no-op steps escalate and then stop.
4. Fix how escalation is spent.
5. Element-indexed, row-grouped screen text.
6. Fix the ledger deadlock.

Together these address every failure in the audited runs. The impact estimates below are inferences from the logs, not measurements.

### Cited Findings
Each fix lists where to change it, what to change, the evidence, and the expected effect.

1. **Keyboard and focus signal, and a self-focusing type_text.**
   - Where: `Agent.look()` (Agent.kt:632-642), `Screen`, `ContextBuilder.perStep` (ContextBuilder.kt:162-167), `AgentTools.type_text` (AgentTools.kt:107-110), SYSTEM_PROMPT line 829.
   - What: detect the keyboard from OCR (≥ 8 single-letter or "123"/"space" boxes in the bottom 45% in QWERTY rows) or from pixels. Drop those boxes from the list and add one line: "KEYBOARD IS UP: a text field is focused. Use type_text now." Give type_text optional `x`/`y`/`id` and `submit`, and change the rule to "type_text focuses the field for you".
   - Evidence: the 14-11-27 loop (6+ taps). 40 of 339 screen steps had the keyboard up, with an average of 20.6 key boxes each [agg].
   - Effect: removes the focus-loop failure class and saves 1–2 steps on every search (tap → type → return becomes one action). It also cuts per-step screen tokens on keyboard screens by about 60% (20.6 of about 33 boxes).
2. **settle() rewrite.**
   - Where: Agent.kt:594-610; CoreDeviceBackend.capture (138-156); optionally session.rs (stream frames).
   - What: compare frames with a tolerance (mean absolute difference over a downsampled grayscale, excluding the status bar). Cap the total at about 1.2–1.5 s. Keep the last full PNG so `look()` reuses it instead of taking another shot. Optionally decode the video stream.
   - Evidence: tap act median 4.1 s and p90 8.7 s against 1.8–2.6 s on static screens. Act is the largest time bucket overall (1,205 s).
   - Effect: an estimated 30–45% less wall time per Haiku step (for example 14-11-27 would go from about 12 s to about 5 s per tap step). No cost change.
3. **A single progress meter in StuckDetector.**
   - Where: StuckDetector.kt (rewrite `looked` and `stepUp`); the Agent passes `progress: Boolean` for every step, including refused or non-phone ones (Agent.kt:264-299, 341-548).
   - What: no progress for 3 steps escalates once (to the brain's decide or Sonnet); 6 steps stops. Treat "same page after tap" as no progress. Counters are not reset unless an escalation really ran.
   - Evidence: 14-05-46 S15–S30 (16 wasted steps); 13-50-30 S15–S25 (9 held back); 14-11-27 (6 same-page taps).
   - Effect: caps any loop at about 6 steps instead of 30. The estimated median steps on failed runs drop from 30 to under 15. Failing tasks fail faster and cheaper (about 50% fewer steps), and the honest-stop rate improves.
4. **Spend escalation on decisions, not trivia.**
   - Where: Agent.kt:287, 393, 415 (remove those `critical()` calls); Agent.kt:297-298 (budget rule); ModeSettings.agentConfig.
   - What: exclude the brief from the step-up budget, or reserve about $0.03 for one decide or step-up, and prefer the brain's text-only `decide` (about $0.005 per the commit message) over a full Sonnet vision step (about $0.025). Auto-call `decide` at the first scroll reversal after a Sort/Filter, or when SEEN WHILE SCROLLING has at least 2 candidates.
   - Evidence: Sonnet spent on "type sushi" (14-05-46 S5), open_app (13-22-48 S3) and a promo tap (13-04-40 S8). In 13-04-40 the Sonnet steps did all the hard taps.
   - Effect: higher success on "choose the best" tasks, the class behind all Swiggy failures. Cost about the same or lower ($0.005 decide vs $0.025 step-up).
5. **Structured screen text with element ids.**
   - Where: `CoreDeviceBackend.readText` / `Agent.screenTexts` (Agent.kt:677-685) and `ContextBuilder.perStep`.
   - What: sort by (y, x); merge boxes on the same line; cluster into rows or cards by vertical gaps; attach "AD/Sponsored" to their card; drop status-bar boxes. Number each element and accept `tap {id}`. Build SEEN WHILE SCROLLING from cards, not lines (Agent.kt:278-283).
   - Evidence: 14-01-05 S9 and 14-05-46 S24 (fragmented cards, detached AD labels); Qwen's coordinate errors in 13-50-30.
   - Effect: makes comparison across scrolls feasible for Haiku. Removes coordinate-convention failures for any model, and so widens the cheap-model choice (Qwen, Gemini Flash). Exact tap descriptions improve the ledger and the history.
6. **ChangeLedger "no effect" path and label-based classification.**
   - Where: ChangeLedger.looked (ChangeLedger.kt:47-52); Changes.isChange (Changes.kt:36-37); Agent.kt:480-492.
   - What: if the next look is the same page with no new text, drop the pending entry with the note "your tap did not register; try again or tap elsewhere". Classify changes only by the button label, or the id's text.
   - Evidence: the 13-50-30 deadlock (S10–S25); the 12-42-09 S4 "Log" false positive.
   - Effect: removes deadlocks while keeping the duplicate-add protection that 12-42-09 motivated.
7. **Memory hygiene.**
   - Where: ContextBuilder.stable (ContextBuilder.kt:80-118) and the memory recall that builds TaskContext.
   - What: include ABOUT THE USER facts and tips only when they match the task's app or entities. Drop "could not finish" RECENT TASKS for new tasks. Mark list data (ratings) as "may be stale".
   - Evidence: 13-22-48 (Snacks misdirection); 13-30-58 (stale ratings copied into a note); 14-11-27 (Sonali facts in a Swiggy task).
   - Effect: fewer wrong priors and a smaller prefix.
8. **Brief reliability and latency.**
   - Where: Brain.brief (Brain.kt:55-68).
   - What: log raw arguments on a parse failure. Accept `steps` as a string by splitting lines. Run the brief in parallel with step 1's look, or skip it for simple tasks.
   - Evidence: 3 of 8 recent briefs were "unknown" failures; 7.5–9.8 s before the first action.
   - Effect: about 8 s saved per task. Avoids paying $0.015 for nothing.
9. **Scroll distance and end detection.**
   - Where: AgentTools.toOp scroll (AgentTools.kt:275-287); Agent history annotation.
   - What: 0.6–0.7 of the height. When a scroll is "unchanged", say "end of list reached".
   - Evidence: 14-01-05 S9 (name hidden under the sticky header); 13-22-48 (never reached Snacks).
   - Effect: fewer scroll steps and fewer reversals.
10. **Trim the system prompt and tool schemas.**
    - Where: Agent.kt:792-925; AgentTools.kt:57-83, 191-198.
    - What: move the Markdown reply rules into the `done` description. Drop `item` and `expect` (low value, and `expect` echoes add noise). Shorten the incident-specific rules.
    - Evidence: a 9.7k-character prompt; the expectation echo did not help in 14-11-27.
    - Effect: lower cost on cache misses and on non-caching providers, and less instruction dilution.

### Inferences
- Expected aggregate effect, estimated from these logs:
  - Success: fixes 1, 3, 4, 5 and 6 address every failure mode seen in the six failing or looping runs.
  - Steps per task: about −30–50% (no focus loops, no plan churn, one-step typing, larger scrolls).
  - Latency: about −40–60% per task (settle fix, brief in parallel, fewer steps).
  - Cost: similar or lower per successful task. Fewer steps and decide in place of Sonnet step-ups outweigh slightly more structured text.
- Fixes 1–3 are small, local code changes with no model dependency. They should land first and be checked with a re-run of the Swiggy and MyFitnessPal tasks.

### Gaps
- None of the impact estimates are measured. They need an A/B on the same tasks (the user's bake-off) to confirm.
- Whether OCR-based keyboard detection is robust for non-English keyboards and third-party keyboards is untested.
