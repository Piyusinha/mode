# Open-source phone-agent harnesses: how the reliable ones are built (as of Nov 2026)

Scope note: 18 tool calls; primary sources were arXiv full-text HTML (Mobile-Agent-E, Mobile-Agent-v3, AndroidWorld, MobileWorld, minitap mobile-use, Agent S3/BJudge) plus project pages. Many 2026 AndroidWorld numbers are vendor self-reports. Dates are marked where the material is older than 2026.

## 1. Which projects lead in 2026, and what success rates do they report?

### Takeaway
AndroidWorld (116 tasks) is saturated. Harness-style systems (multi-agent frameworks that wrap frontier LLMs) self-report 91–100%. End-to-end open GUI models sit around 66–73%. On the harder 2026 MobileWorld benchmark, the best harness drops to about 52%. The biggest gains come from the harness design (decomposition, verification, memory), not from the model alone.

### Cited Findings
- Leaderboards conflict. An AskUI snapshot (May 2026) lists AGI-0 97.4%, AskUI 94.8%, DroidRun 91.4%, Surfer 2 87.1%, gbox.ai 86.2%, and a human baseline of 80.0%. BenchLM (verified Oct 28, 2026) shows Qwen3.8-Omni-Flash on top at 87.1% and says its rows are compiled from self-reports. — [AskUI benchmarks](https://www.askui.com/benchmarks); [BenchLM AndroidWorld](https://benchlm.ai/benchmarks/androidworld)
- **minitap mobile-use** (open source, Apache-2.0) reports 100% on AndroidWorld (116/116), 20 points above humans. Its paper is "Do Multi-Agents Dream of Electric Screens?" (arXiv 2602.07787, Feb 2026). — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1); [minitap benchmark](https://minitap.ai/benchmark)
- **DroidRun** (repo now `droidrun/mobilerun`) reports 91.4% on AndroidWorld (106/116) with GPT-5, in a chart dated 03.10.2025 (older). The README now also claims a "mobile-harness" at 100% AndroidWorld and 98.34% iOSWorld. Those claims are unverified. — [DroidRun benchmark](https://droidrun.ai/benchmark/); [GitHub droidrun](https://github.com/droidrun/droidrun)
- **Mobile-Agent-v3 / GUI-Owl** (X-PLUG/Alibaba, Aug 2025, older): GUI-Owl-7B scores 66.4 on AndroidWorld and 29.4 on OSWorld. The Mobile-Agent-v3 framework (GUI-Owl-32B) scores 73.3 on AndroidWorld and 37.7 on OSWorld, and claims SOTA among open-source frameworks at the time. — [arXiv 2508.15144](https://arxiv.org/html/2508.15144)
- **Mobile-Agent-E** (Jan 2025, older), on its own long-horizon Mobile-Eval-E with GPT-4o, reports a Satisfaction Score of 75.1 (86.9 with self-evolution). The baselines are v2 at 53.0, v1 at 45.5, and AppAgent at 25.2. With Claude-3.5-Sonnet it scores 75.5 (83.0 with Evo). — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **UI-TARS-2** (ByteDance, Sep 2025, older) reports AndroidWorld 73.3, OSWorld 47.5, WindowsAgentArena 50.6, and Online-Mind2Web 88.2. — [arXiv 2509.02544](https://arxiv.org/abs/2509.02544)
- **Agent S3** (Simular, late 2025) scores 68.1% on AndroidWorld with GPT-5. With Behavior Best-of-N (BJudge, N=3) it scores 71.6%. On OSWorld, BJudge reaches 72.6% (GPT-5 + Opus 4.5), against a human level of 72.36%. — [arXiv 2510.02250](https://arxiv.org/html/2510.02250)
- **AndroidWorld's original baseline, M3A** (Google, May 2024, older), scores 30.6% (GPT-4 Turbo, a11y tree). The human baseline is 80.0%. — [arXiv 2405.14573](https://arxiv.org/html/2405.14573)
- **MobileWorld** (Dec 2025/Jan 2026) has 201 tasks, 62.2% of them multi-app, with an average of 27.8 steps versus 14.3 for AndroidWorld. Scores: GPT-5 + UI-Ins-7B grounder 51.7%, Gemini-3-Pro 46.3%, Claude-4.5-Sonnet 43.8%, Doubao-1.5-UI-TARS 20.9%, GUI-Owl-7B 4.5%. The paper calls AndroidWorld saturated at over 90%. — [arXiv 2512.19432](https://arxiv.org/html/2512.19432)
- **Open-AutoGLM** (zai-org) is an open phone-agent framework plus model (Apache-2.0 code, MIT models). Its action set is `do(action=...)` with Launch, Tap, Type, Swipe, Back, Home, Long Press, Double Tap, Wait, and Take_over (hand control to the human for login, captcha, or payment), plus `finish(message=...)`. These details come from third-party summaries. — [DeepWiki Open-AutoGLM action system](https://deepwiki.com/zai-org/Open-AutoGLM/6-action-system); [DEV Community](https://dev.to/wonderlab/open-source-project-of-the-day-part-29-open-autoglm-a-phone-agent-framework-for-controlling-4p8k)
- **AppAgentX** (Westlake, Mar 2025, older) evolves high-level "shortcut" actions from its execution history. Secondary summaries report task time falling from 209.3s to 91.2s, success rising from 70.8% to 71.4%, and tokens falling from 9.26k to 4.94k (-47%). The exact configuration was not confirmed. — [arXiv 2503.02268](https://arxiv.org/abs/2503.02268v2); [alphaXiv overview](https://www.alphaxiv.org/overview/2503.02268)

### Inferences
- For a small team, the most relevant open-source references are minitap mobile-use (best documented ablations and a cheap mixed-model configuration), Mobile-Agent-E (a vision-only Perceptor like Mode's OCR approach, with explicit loop and termination rules), and Agent S3 (a flat worker loop beat a hierarchical manager).
- Treat 95–100% AndroidWorld claims as upper bounds. MobileWorld's ~50% is closer to what real consumer apps (e-commerce, multi-app) are likely to produce.

### Gaps
- I did not verify midscene.js, OS-Copilot, Agent-S2 alone, or the latest Open-AutoGLM/AutoGLM numbers. Midscene was not fetched.
- There is no official, independently verified AndroidWorld leaderboard snapshot for Nov 2026. Sources disagree.
- The internals of DroidRun's iOS path (`mobilerun-ios`) were not documented on the page. It is unknown whether it relies on WebDriverAgent/XCUITest.

## 2. Perception: screenshot vs accessibility tree vs set-of-marks vs OCR+icon detection; keyboard/focus detection

### Takeaway
The strongest 2026 harnesses use hybrid perception: a structured element list for targeting plus a screenshot for context and verification. Hybrid perception alone was worth +11 points in minitap's ablation. When no accessibility tree is available (Mode's iOS case), Mobile-Agent-E's vision-only Perceptor is the closest proven template: OCR text plus detected icons with coordinates, fed alongside the screenshot. Focus and keyboard state is best handled deterministically in code, not by the LLM.

### Cited Findings
- **minitap:** the UI hierarchy is the primary source for element targeting, and screenshots are used selectively. The Cortex is told to "use structure for targeting and visuals for context and verification." Removing hybrid perception cost 11 points (100 to 89). — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **minitap focus and keyboard handling:** focus is tried by resource ID, then by coordinates, then by text match. The cursor moves to the end of existing text, and deterministic code (not the LLM) handles focus, keyboard, and encoding. In the single-agent baseline, 12 of 42 failures (the biggest single class) were "text input failures where the agent believed input succeeded." — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **Mobile-Agent-E's Perceptor** is vision-only (no XML). It uses DBNet OCR detection, ConvNextViT-document OCR recognition, GroundingDINO icon grounding, and Qwen-VL-Plus icon captions, and outputs text and icons with coordinates. The Manager does not get Perceptor output, only the screenshot. The Operator gets the screenshot plus the Perceptor list. — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **M3A in AndroidWorld (2024, older):** with GPT-4 Turbo, the a11y tree alone scored 30.6 and SoM (screenshot with numbered marks plus element list) scored 25.4. With Gemini 1.5 Pro, a11y scored 19.4 and SoM 22.8. The best modality is model-dependent. — [arXiv 2405.14573](https://arxiv.org/html/2405.14573)
- **OmniParser V2** (screenshot-only parsing into interactive elements and captions) runs at 0.6 s/frame on an A100 and 0.8 s on a 4090. It raises GPT-4o's ScreenSpot-Pro score from 0.8 to 39.6. — [Microsoft Research blog](https://www.microsoft.com/en-us/research/articles/omniparser-v2-turning-any-llm-into-a-computer-use-agent)
- **MobileWorld's harness** uses a planner LLM that describes the target element in natural language and a separate 7B grounder (UI-Ins-7B) that converts it to pixels. The authors argue this decoupling lets general LLMs work without native pixel grounding. — [arXiv 2512.19432](https://arxiv.org/html/2512.19432)
- **DroidRun/Mobilerun** combines accessibility trees with screenshots, with `--vision-only` as a fallback for apps lacking accessibility data. — [GitHub droidrun](https://github.com/droidrun/droidrun)
- **GUI-Owl** keeps only the most recent 1–3 screenshots in context to save memory. — [arXiv 2508.15144](https://arxiv.org/html/2508.15144)
- **Agent S3's behavior narratives** draw a marker at the tap coordinate on the "before" screenshot and use a zoomed crop around the pointer on the "after" screenshot, taken 3 s after the action to catch delayed changes. — [arXiv 2510.02250](https://arxiv.org/html/2510.02250)

### Inferences
- Mode's on-device OCR with positions is equivalent to Mobile-Agent-E's text Perceptor. The missing piece is icon/element detection: unlabeled buttons, search-field outlines, "+" buttons, and toggles. A small on-device detector (OmniParser-style, or Apple Vision rectangle/saliency) could fill it.
- **Keyboard and focus without an a11y tree:** detect the iOS keyboard deterministically (OCR of the QWERTY rows or the "space"/"return" keys in the bottom ~40% of the screen, or a pixel-region template) and inject a fact such as `keyboard_visible: true; focused field likely: <OCR text nearest caret>`. Then make "type" the only allowed action when the keyboard is up and the target field is focused. This directly targets the failure of tapping a focused search field 6 times. It follows minitap's rule of handling focus in code, not in the LLM.
- Send the "brain" (Sonnet) the screenshot only, and the "hands" (Haiku) the OCR element list plus screenshot. This mirrors Mobile-Agent-E's Manager/Operator split and saves tokens.

### Gaps
- I found no published ablation of OCR-only versus OCR+icon detection on a phone benchmark.
- I found no source describing iOS keyboard detection specifically. The approach above is an inference.

## 3. Action space: element index vs coordinates; high-level vs raw actions; avoiding repeated taps

### Takeaway
Harnesses that target elements (by index or selector) and offer composite actions ("tap field, type, and press enter"; "open app") are more reliable and faster. Coordinate-only action spaces are used by end-to-end models (GUI-Owl, UI-TARS, AutoGLM). Repeated taps are prevented by hard rules (identical action more than 3 times in a row means termination or escalation), by read-back verification of typed text, and by high-level composite actions that remove the intermediate tap.

### Cited Findings
- **Mobile-Agent-E's atomic actions** are Open_App(name), Tap(x,y), Swipe, Type(text), Enter, Switch_App, Back, Home, and Wait (10 s), plus learned **Shortcuts** with explicit preconditions. The example "Tap_Type_and_Enter" requires a text box on screen and saves two perception-reasoning iterations. Shortcut usage was 12–15% of actions. — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **AppAgentX:** high-level evolved actions cut time from 209.3s to 91.2s and tokens by about 47% at equal or slightly better success. It falls back to basic actions when a high-level action fails (secondary summaries). — [alphaXiv overview](https://www.alphaxiv.org/overview/2503.02268)
- **Mobile-Agent-v3's mobile actions** are key, click(x,y), long_press, swipe, type, answer, system_button (Back/Home/Menu/Enter), open(app by name), wait, and terminate. They are coordinate-based. — [arXiv 2508.15144](https://arxiv.org/html/2508.15144)
- **minitap:** the Cortex names a target with multiple selector types as fallbacks plus the intended text. The ExecutorToolNode runs tool calls sequentially and aborts the remaining batch when one fails. Removing sequential execution cost 12 points. — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **M3A** outputs JSON actions that reference a SoM mark index. The AndroidWorld action format accepts either an index or x/y. — [arXiv 2405.14573](https://arxiv.org/html/2405.14573)
- **Open-AutoGLM** adds `Take_over` (hand control to the user) and optional confirmation for sensitive taps. Sources disagree on whether Tap takes `[x,y]` or a text description. — [DeepWiki](https://deepwiki.com/zai-org/Open-AutoGLM/6-action-system); [remoteopenclaw skill page](https://www.remoteopenclaw.com/skills/aradotso/trending-skills/open-autoglm-phone-agent)
- **MobileWorld adds `ask_user` and `mcp_call` actions.** Agents that never ask hallucinate missing information, and some over-ask (Doubao: 32.4% success but a low quality score of 0.13). — [arXiv 2512.19432](https://arxiv.org/html/2512.19432)
- **Repeated-action guards elsewhere:** Mozilla Pilo hashes an action signature and debates excluding the element ref so infinite-scroll feeds don't false-trigger. NudgeOps blocks an exact repeat after two attempts. A practitioner suggests hashing page state between steps. — [Pilo issue #430](https://github.com/mozilla/pilo/issues/430); [NudgeOps](https://pypi.org/project/nudgeops/0.2.2/); [Reddit snapshot](https://reddit.sentinel-team.org/posts/1t0kqv7/snapshots/2026-05-01T22%3A04%3A17.338364Z)

### Inferences
- Even with pixel actions over CoreDevice, Mode can expose **element-index actions over OCR boxes**: the model says `tap(el=17)` and the harness resolves the box center. This removes coordinate hallucination and lets the harness key repeat detection on element identity plus screen hash.
- Add composite actions that are generic, not task-specific: `type_into(el, text, submit)` (tap only if the keyboard is not already up, type, optionally return, then read back via OCR), `open_app(name)`, `scroll_until_text(text, max_n)`, and `go_back` (try the top-left "<" / back label via OCR before the edge swipe, since apps ignore the swipe). This fits the "no hard-coded tasks" rule: they are hands, not plans.

### Gaps
- I found no published head-to-head ablation of index-based versus coordinate-based actions with the same model on a phone benchmark in 2026.

## 4. Planning and verification: manager/operator splits, reflection, verifiers, progress, loop and oscillation, choosing across screens

### Takeaway
Three things carry the evidence:
- **A per-action verifier** (before/after comparison with explicit success/failure categories). It was worth 15 points in minitap and is the core of Mobile-Agent-E, v2, and v3.
- **A notetaker/scratchpad** that records facts and options seen across screens, so the agent decides from memory instead of scrolling back. It was worth 8 points in minitap, and memory/state-tracking failures dominate MobileWorld.
- **Hard loop rules plus escalation:** k consecutive failures go to the manager, and an identical action repeated more than 3 times ends or escalates the run.

Hierarchy is not automatically good. Agent S3 removed its manager and gained 8.8 points while halving LLM calls.

### Cited Findings
- **Mobile-Agent-E's Action Reflector** compares the screens before and after the action. Outcomes are A (success or partial success), B (failed, wrong page), and C (failed, no change), with error descriptions. The Operator gets the last m=5 actions and errors. After **k=2 consecutive failures**, the Manager receives recent errors and revises the plan. — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **Mobile-Agent-E's termination rules:** 40 max iterations, 3 consecutive errors, or **the same action more than 3 times in a row (excluding Swipe and Back)**. The termination error rate fell from 52% (v2) to 32% (E) and 12% (E+Evo) with GPT-4o. — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **Mobile-Agent-E's Notetaker** extracts task-relevant information (prices, phone numbers) from each new screen into notes. The Manager tracks plan, subgoal, and **progress** fields. — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **Mobile-Agent-v3:** the Reflector classifies each transition as SUCCESS or FAILURE with causal feedback to the Manager. The Notetaker runs only on SUCCESS and stores critical elements. The Manager re-prioritizes or inserts corrective subgoals. The paper reports no component ablations. — [arXiv 2508.15144](https://arxiv.org/html/2508.15144)
- **minitap ablation (AndroidWorld, from 100%):**

  | Component removed | Points lost |
  |---|---|
  | Multi-agent architecture | 21 |
  | Post-validation | 15 |
  | Sequential execution | 12 |
  | Hybrid perception | 11 |
  | Meta-cognitive reasoning | 9 |
  | Scratchpad memory | 8 |
  | Data-fidelity prompts | 3 |
  | Video recording | 2 |

  The abstract says verified execution adds +7 while the table says 15, an internal inconsistency. — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **minitap's meta-cognitive reasoning:** before each decision, the Cortex reviews prior outputs for **cycle detection** (sequences returning to earlier states with no progress), **strategy evaluation** (has this approach failed?), and **evidence accumulation** (what's already done, to avoid redundant work). No numeric thresholds are given. The scratchpad tools are `save_note`, `read_note`, and `list_notes`. Subgoal failure triggers the Planner to replan. — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **minitap's verified text input:** focus, then position the cursor, then type, then re-query the hierarchy, then read back the field, then return an explicit expected-vs-actual comparison to the Cortex. — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **minitap single-agent baseline failure mix (42 failures at 64% success):**
  - Context management: 16
  - Execution reliability: 17, of which 12 were text input
  - Error recovery: 9, including 5 repetitive loops

  — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **Agent S3:** removing hierarchy (a flat worker that can replan at any step) improved OSWorld from 48.8% (S2) to 57.9% (S2 without hierarchy) to 62.6% (S3). LLM calls per task fell from 73.6 to 35.1 (-52%) and time per task fell 62%. — [arXiv 2510.02250](https://arxiv.org/html/2510.02250)
- **BJudge verification of whole runs:**
  - Each step becomes a "fact" generated from before/after screenshots.
  - A single multiple-choice judge picks the best of N rollouts.
  - Behavior narratives score 60.2%, against 56.0% for screenshots only and 55.0% for trajectory summaries.
  - Judge accuracy is 78.4%. Most judge errors (8/12) were narrative hallucinations.

  — [arXiv 2510.02250](https://arxiv.org/html/2510.02250)
- **M3A's per-step reflection (2024):** after each action, a summary of intended action, success/failure, likely reason, and recommendation becomes the action history. M3A-Simple (without it) scored 19.8 versus 30.6. — [arXiv 2405.14573](https://arxiv.org/html/2405.14573)
- **MobileWorld failure modes** include repeated actions from forgetting completed work (re-renaming files already renamed), arithmetic and logic errors comparing cart items, hallucinated dates, and premature termination. — [arXiv 2512.19432](https://arxiv.org/html/2512.19432)
- **Mobile-Agent-E self-evolution "Tips"** (lessons learned) alone raised the Satisfaction Score from 79.7 to 87.5 (GPT-4o) and from 75.6 to 85.2 (Claude). — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)

### Inferences (mapped to Mode's observed failures)
- **Re-adding to cart or diary because the confirmation flashes:** use a delayed post-action capture (Agent S3 uses +3 s), or multiple captures, plus a state-based verifier. Ask "is the item count / diary entry now present?", not "did a toast appear?". Write a note on success ("added X to cart, qty 1"), so the evidence-accumulation check blocks a re-add. Before any "add" action, the harness can require a check against notes.
- **Scrolling a list up and down 10+ steps:** use a Notetaker that records every candidate option (name, price, rating, screen position) as the list scrolls. Then force a decision once the list end is reached or after N scrolls, and hand the comparison to the brain from notes, not screens (as in Mobile-Agent-E and minitap). Add oscillation detection: an up/down/up swipe pattern, or a screen hash returning to a previously seen state, triggers "decide now from notes."
- **Re-planning repeatedly instead of acting:** Agent S3's evidence suggests escalating to the planner only on k=2 consecutive verified failures (Mobile-Agent-E rule), not on a schedule or on the hands' request. Otherwise use a flat loop where the worker can locally adjust.
- **Apps reopening on a stale page:** minitap's Contextor checks the foreground app each cycle and relaunches it, and the Reflector's "B: wrong page" category feeds back. For Mode, verify the first screen against the brief's expected start, and on mismatch use a generic `reset_app` (kill and relaunch, then navigate to root).
- **Back swipe ignored:** classify "C: no change" after a back swipe, and have the hands' composite `go_back` fall back to tapping the OCR'd back chevron or label.

### Gaps
- No source gave numeric cycle-detection thresholds beyond Mobile-Agent-E's "more than 3 identical actions."
- I did not find browser-use's actual loop-detector implementation; the search returned other projects.

## 5. Speed and cost: tokens per step, latency, prompt-size reduction

### Takeaway
Reported costs are around $0.7–1.6 per task for frontier harnesses, and per-step latency is seconds to tens of seconds. The proven savers are:
- Mixing cheap models for non-reasoning roles. minitap cut cost 32% with no success loss, though downgrading the core reasoner was catastrophic.
- Composite and shortcut actions (-47% tokens, -56% time in AppAgentX).
- A flat loop instead of a hierarchy (-52% LLM calls in Agent S3).
- Keeping only 1–3 recent screenshots in context.

### Cited Findings
- **minitap cost:** $1.07 per task in the mixed configuration versus $1.58 with Gemini 3 Pro for all roles (-32%). Average completion is 31 s, versus 68 s for the single-agent baseline. Model mix: the Cortex on Gemini 3 Pro, the Planner on Llama 4 Scout, the Executor on Llama 3.1 70B, the Orchestrator on GPT-OSS 120B, the Contextor on Llama 3.1 8B. Swapping the Cortex to an 8B model collapsed success to 11.2%. Swapping other roles to small models gave 50.9–57.8%. — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **minitap controller choice:** high-level automation frameworks add 600–1400 ms per action, which motivated their choice of a lower-level controller. Swapping UIAutomator2 for Maestro cost 5 points. — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **Agent S3/BJudge cost:** $0.72 per single rollout, $0.11 for narrative generation, and $0.03 for judging N=10. S3 cut time per task from 2367 s to 891 s. — [arXiv 2510.02250](https://arxiv.org/html/2510.02250)
- **Mobile-Agent-E seconds per operation (perception + reasoning):** 27–41 s. Evolution and shortcuts reduced GPT-4o's from 30.1 s to 27.4 s. — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **M3A** averaged 3.9 min per task, or 2.5 min for the text-only variant (2024). — [arXiv 2405.14573](https://arxiv.org/html/2405.14573)
- **OmniParser V2** takes 0.6–0.8 s per frame on GPU and is 60% faster than V1 thanks to a smaller caption input. — [Microsoft Research](https://www.microsoft.com/en-us/research/articles/omniparser-v2-turning-any-llm-into-a-computer-use-agent)
- **History limits:** GUI-Owl keeps only the last 1–3 images. minitap's Summarizer drops old messages past a threshold. — [arXiv 2508.15144](https://arxiv.org/html/2508.15144); [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **MobileWorld** flags context overflow (a ~20k-token tool output swamping context) as a failure mode. — [arXiv 2512.19432](https://arxiv.org/html/2512.19432)

### Inferences
- minitap's result supports Mode's split. Cheap models are fine for perception and plumbing roles, but the step-to-step "what next" decision needs a strong reasoner. If Haiku is making every next-action decision, the evidence says that is where failures concentrate. A middle path is to let Haiku act, while repeated-failure, oscillation, and choice points escalate to Sonnet automatically.
- Cut tokens with these moves:
  - Send OCR element lists as compact indexed lines.
  - Include only the latest screenshot, plus one previous screenshot for the verifier.
  - Downscale screenshots for the brain.
  - Keep the brief and notes in a cached prompt prefix.
  - Make the verifier a cheap call, or deterministic where possible (screen-hash unchanged means "C: no change" without an LLM).

### Gaps
- Few projects publish tokens per step. No 2026 source gave per-step token counts for phone agents.

## 6. What transfers to iOS without XCUITest/WebDriverAgent?

### Takeaway
Almost all of the reliability techniques are harness logic that doesn't depend on an accessibility tree:
- per-action reflection and verification
- notetaker/scratchpad
- loop rules and escalation
- composite actions with read-back
- delayed post-action capture
- foreground-app checks
- flat loops
- model mixing
- shortcuts/tips memory

What does not transfer is a11y-tree element targeting (DroidRun Portal, minitap's UIAutomator hierarchy, M3A a11y). Vision-only perception (OCR + icon detection + screenshot) has to replace it, as in Mobile-Agent-E, Mobile-Agent-v3/GUI-Owl, UI-TARS, and MobileWorld's planner+grounder.

### Cited Findings
- **Mobile-Agent-E's Perceptor is vision-only** (no XML) and still reached a Satisfaction Score of 75–87. — [arXiv 2501.11733](https://arxiv.org/html/2501.11733)
- **Pure-screenshot coordinate frameworks:** GUI-Owl/Mobile-Agent-v3 uses coordinate actions from screen captures, at 73.3 on AndroidWorld. — [arXiv 2508.15144](https://arxiv.org/html/2508.15144)
- **MobileWorld's planner + 7B grounder** shows a general LLM can plan in language while a small grounding model resolves pixels. — [arXiv 2512.19432](https://arxiv.org/html/2512.19432)
- **minitap's ablation implies an a11y-free cost:** removing hybrid perception cost 11 points, and the a11y-based text read-back is part of the 15-point post-validation gain. On iOS, OCR read-back is the substitute. — [arXiv 2602.07787](https://arxiv.org/html/2602.07787v1)
- **DroidRun/Mobilerun supports real iPhones over USB** (`mobilerun-ios`) and an iOS Portal app for the Simulator. The mechanism is not described. — [GitHub droidrun](https://github.com/droidrun/droidrun)
- **Agent S3's verification inputs** are a tap marker on the before-shot plus a zoomed crop on the after-shot. This is pure image processing and platform-agnostic. — [arXiv 2510.02250](https://arxiv.org/html/2510.02250)

### Inferences
Priority adoption list for Mode, ordered by evidence strength and ease:
1. **Per-action verifier with A/B/C outcomes:** capture after a delay (0.5 s and 2–3 s, to catch flashing confirmations), use a deterministic screen-hash "no change" check, and run a cheap LLM check only when needed.
2. **Notetaker/scratchpad plus evidence-accumulation check:** blocks re-adds and enables choosing among options seen across screens.
3. **Hard loop rules:** identical (action, element, screen-hash) more than 2–3 times, or scroll-direction oscillation, forces escalation to the brain with notes. Two consecutive verified failures trigger a replan. A replan budget stops endless re-planning.
4. **Keyboard/focus detection in code,** plus a `type_into` composite with OCR read-back.
5. **Element-index actions over OCR/icon boxes** instead of raw coordinates from the model.
6. **Generic `go_back`** that falls back from the edge swipe to tapping a back chevron. **Foreground-app/start-page check with relaunch.**
7. **Flat loop** with escalation only on failure signals (Agent S3), and a cheap model only for non-decision roles (minitap).
8. **Tips memory** across runs (Mobile-Agent-E: +7.8 Satisfaction Score with GPT-4o from tips alone). It stays generic, so it fits "no hard-coded tasks."

### Gaps
- I found no open-source project documenting a CoreDevice-based (non-XCUITest) iPhone control path.
- I found no published iOS-specific benchmark harness details. "iOSWorld" is mentioned only in DroidRun's unverified claim.
