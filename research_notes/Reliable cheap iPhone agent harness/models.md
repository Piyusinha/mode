# Models for a cheap, fast, accurate phone-control agent (as of 1 Nov 2026)

Scope: models for (a) GUI grounding (where to tap), (b) step-by-step action choice ("hands"), (c) planning/judgement ("brain") for Mode, an iPhone agent that calls OpenRouter with the user's own key. Targets: under ~3 s per step decision, $0.01–0.05 per task, reliable taps.

Note on sources: almost every benchmark number for late-2026 models is **vendor self-reported** and reaches us through aggregators (BenchLM, aiidelist, emergent.sh). BenchLM itself says its AndroidWorld rows are compiled "from provider self-reports and secondary reports". Prices come from the live OpenRouter API (`https://openrouter.ai/api/v1/models`, pulled 2026-11-01). That API returned no latency or throughput numbers (`latency_last_30m` was null for every endpoint), so latency comes from Artificial Analysis and vendor posts.

---

## 1. Latest GUI benchmarks: who leads, and which cheap models come close?

### Takeaway
On grounding (ScreenSpot-Pro), the leaders are expensive frontier models (GPT-6 Astra 92.7%, Claude Opus 4.8 87.9%). The best cheap or open grounders are Meta Muse Glimmer 30B (75.4%) and H Company's Holo2 models (57–71%). On mobile tasks (AndroidWorld), the top rows are all Qwen 3.8 models with self-reported scores (Qwen3.8-Omni-Flash 87.1%), followed by Holo3.1 (79.3% for 35B-A3B, 71% for 4B/9B). Claude Haiku 5.5 has no published ScreenSpot or AndroidWorld score. Its computer-use evidence is OSWorld 2.1 at 72.4%, against 48.9% for GPT-6 Luna and 83.9% for Sonnet 5.5, all as reported by Anthropic.

### Cited Findings

**ScreenSpot-Pro** (1,581 grounding instructions, 23 professional desktop apps, 3 operating systems; static screenshot localization; micro-average accuracy taken "from the official leaderboard". BenchLM warns that "cropping, visual search, planners, tools, image resolution, and decoding policy can change results, so rows may not be directly comparable".) Source: [BenchLM ScreenSpot-Pro](https://benchlm.ai/benchmarks/screenspot-pro)
- 1 GPT-6 Astra 92.7% · 2 Claude Opus 4.8 87.9% · 3 GPT-5.4 85.4% · 4 Qwen3.8 Max 84.5% · 5 Gemini 3.1 Pro 84.4% · 6 Meta Muse Spark 84.1% · 7 Claude Opus 4.6 83.1% · 8 Qwen3.7 Plus 79.0% · 9 **Muse Glimmer 30B 75.4%** · 10 Gemini 3 Pro 72.7% · 11 Holo2-235B-A22B 70.6% · 12 Qwen3.6 Plus 68.2% · 13 **Holo2-30B-A3B 66.1%** · 14 Qwen3.5 397B 65.6% · 15 **Holo2-8B 58.9%** · 16 Nemotron 3 Nano Omni 30B-A3B 57.8% · 17 **Holo2-4B 57.2%** · 18 **LFM3-VL-2B 55.5%** · 19 Claude Opus 4.5 45.7% — [BenchLM](https://benchlm.ai/benchmarks/screenspot-pro)
- That page has no Flash, Luna, Haiku, UI-Venus or GUI-Owl rows — [BenchLM](https://benchlm.ai/benchmarks/screenspot-pro)
- A Baidu wiki entry reports Indeed-UI-32B at 82.7 and Indeed-UI-8B at 81.0 on ScreenSpot-Pro (Sept 2026). An older (March 2026) snapshot had KV-Ground-GuiOwl1.5-0315-8B-ZoomIn first at 80.5. These specialist 8B models are therefore competitive with frontier models on grounding, but they are absent from the BenchLM table — [Baidu wiki](https://baike.baidu.com/en/item/ScreenSpot-Pro/4972148); [vectoringai](https://vectoringai.com/posts/benchmark/ScreenSpot-Pro.html). The official leaderboard (gui-agent.github.io/grounding-leaderboard) was not checked directly.

**AndroidWorld** (interactive Android task completion. BenchLM shows it "for reference only" and builds the rows from provider self-reports and secondary reports; page updated 29 Oct 2026.) Source: [BenchLM AndroidWorld](https://benchlm.ai/benchmarks/androidworld)
- 1 **Qwen3.8-Omni-Flash 87.1%** · 2 Qwen3.8 Max 85.3% · 3 Qwen3.8-Flash-Next 84.5% · 4 Qwen3.8-27B 81.9% · 5 Qwen3.7 Plus 81.0% · 6 **Holo3.1-35B-A3B 79.3%** · 7 **Holo3.1-4B 71.0%** · 7 **Holo3.1-9B 71.0%** · 9 Qwen3.6-27B 70.3% · 10 Claude Opus 4.6 62.0% — [BenchLM](https://benchlm.ai/benchmarks/androidworld)
- The Qwen3.8-Omni-Flash 87.1% comes from the vendor's "launch post", and BenchLM says the model lacks "enough non-generated coverage for a public rank" — [BenchLM model page](https://benchlm.ai/models/qwen3-8-omni-flash)
- H Company reports Holo3.1-35B-A3B at 79.3% (Holo3 was 67%) and the 4B/9B models at 71% (from 58%), released 1 June 2026, Apache-2.0 open weights in 0.8B/4B/9B/35B-A3B sizes. Self-reported — [H Company newsroom](https://hcompany.ai/newsroom/holo3.1); [H Company publications](https://hcompany.ai/research/publications); [HF card Holo-3.1-4B](https://huggingface.co/Hcompany/Holo-3.1-4B)

**MobileWorld** (harder, long-horizon, cross-app; includes user-interaction and MCP tasks; max 50 steps)
- Original paper (Dec 2025, independent academic eval). End-to-end models scored low: Doubao-1.5-UI-TARS 20.9%, GELab-Zero-4B 10.9%, UI-Venus-72B 10.4%, Qwen3-VL-235B 9.5%, Qwen3-VL-32B 9.0%, Qwen3-VL-8B 5.5%, UI-Venus-7B 5.5%, GUI-Owl-32B 5.5%, GUI-Owl-7B 4.5% overall — [arXiv 2512.19432](https://arxiv.org/html/2512.19432v1)
- Vendor-reported 2026 numbers, via a third-party write-up: Qwen-UI-Agent 82.1% on the GUI-only split, GUI-Owl-1.5-32B-Instruct 43.9%. These are not directly comparable to the 2025 paper's setting — [aireiter](https://aireiter.com/blog/qwen-ui-agent-benchmarks-access-guide); [Qwen-UI-Agent report](https://tongyi-mai.github.io/Qwen-UI-Agent/)
- The UI-Venus-2 technical report (Ant Group, arXiv 2609.00028) evaluates GUI-only MobileWorld (117 tasks, 50 steps), but its score could not be extracted — [arXiv 2609.00028](https://arxiv.org/pdf/2609.00028)

**OSWorld 2.1 (offline subset), Anthropic-published, not independently reproduced**
- Claude Haiku 5.5 72.4%, Haiku 4.5 15.7%, GPT-6 Luna 48.9%, Sonnet 5.5 83.9%. Haiku 5.5 was released 7 Oct 2026 — [aiidelist](https://aiidelist.com/blog/claude-haiku-5-5-benchmarks); [emergent.sh](https://emergent.sh/learn/claude-haiku-5-5-benchmarks); [X post summarizing release chart](https://x.com/mark_k/status/2107904785286615399)
- Haiku 5.5 reaches about 86% of Sonnet 5.5's OSWorld score — [alekseialeinikov.com](https://www.alekseialeinikov.com/en/blog/topics/ai/claude-haiku-5-5-vs-sonnet-5-5-vs-opus-5-5)
- Other Haiku 5.5 vs Sonnet 5.5 rows (judgement-heavy work): Terminal-Bench 4.0 39.2% vs 70.6%; GDPval-AA Elo 1,620 vs 1,840; Chartography 46.4% vs 61.6% — [aiidelist](https://aiidelist.com/blog/claude-haiku-5-5-benchmarks)

### Inferences
- The Qwen3.8 lead on AndroidWorld is entirely self-reported, and Alibaba holds the top 5 rows. That matches what Mode saw in practice: a high benchmark score did not translate into a usable model, because coordinate format and latency broke it in our harness.
- Haiku 5.5 vs Sonnet 5.5 is a small gap on OSWorld (72 vs 84) and a large gap on agentic, reasoning-heavy work (Terminal-Bench 39 vs 71). This fits our observation that Haiku taps well but judges poorly. It argues for keeping Haiku as hands and calling a stronger brain only at decision points.
- ScreenSpot-Pro measures dense desktop pro apps at high resolution. Phone screens have larger targets, so a grounding gap on ScreenSpot-Pro probably overstates the gap on iPhone UIs. This is unverified (see Gaps).

### Gaps
- No ScreenSpot-v2, OSWorld-G or AndroidControl leaderboard for late-2026 models was retrieved. No independent (non-vendor) AndroidWorld rows were found for 2026 models.
- No published ScreenSpot or AndroidWorld score was found for Claude Haiku 5.5, Sonnet 5.5, GPT-6 Luna, Gemini 3.5 Flash-Lite or Gemini 3.8 Flash.
- No 2026 numbers were found for UI-TARS-2, GUI-Actor, Phi-Ground successors, OpenCUA or SeeClick successors. Holo3.1's ScreenSpot-Pro and OSWorld-G values are in figures on the H Company page but were not in its text.
- No iOS-specific benchmark was found. Every mobile benchmark above is Android.

---

## 2. What is on OpenRouter: price, latency, coordinate format, prompting

### Takeaway
Most of the candidates are on OpenRouter, at very different prices. The cheapest capable "hands" tier is Claude Haiku 5.5 and GPT-6 Luna (both $0.10/$0.50) and Qwen3.8-Omni-Flash ($0.15/$0.47). Of the GUI specialists, only UI-TARS-1.5-7B is on OpenRouter ($0.10/$0.20, one provider, no `tools` support), plus Meta's Muse Glimmer 30B ($0.30/$1.20). Holo3.1 is open weights only. Coordinate conventions differ: Claude wants absolute pixels on the image it actually sees, Gemini uses a 0–999 grid, and Qwen and most GUI specialists use 0–1000 relative values. A harness must therefore convert coordinates per model.

### Cited Findings

**Prices (OpenRouter `/api/v1/models`, USD per 1M input/output tokens, pulled 2026-11-01)** — [OpenRouter models API](https://openrouter.ai/api/v1/models)

| Role candidate | OpenRouter id | In / Out | Notes |
|---|---|---|---|
| Hands | `anthropic/claude-haiku-5.5` | 0.10 / 0.50 | 1M ctx; about 10 endpoints (Anthropic, Bedrock, Vertex, Azure) |
| Hands | `openai/gpt-6-luna` / `gpt-6-luna-pro` | 0.10 / 0.50 | 1.05M ctx |
| Hands | `qwen/qwen3.8-omni-flash` | 0.15 / 0.47 | **single provider (Alibaba)** |
| Hands | `qwen/qwen3.8-flash` | 0.15 / 0.47 | |
| Hands | `xiaomi/mimo-v2.6-flash` | 0.14 / 0.28 | many providers |
| Hands | `bytedance-seed/seed-2.0-mini` | 0.10 / 0.40 | |
| Hands/judge | `google/gemini-3.5-flash-lite` | 0.30 / 2.50 | |
| Hands/judge | `google/gemini-3.1-flash-lite` | 0.25 / 1.50 | |
| Judge | `google/gemini-3.8-flash` | 0.825 / 4.125 | |
| Judge | `openai/gpt-6.1-terra` | 1.00 / 5.00 | positioned for computer use |
| Brain | `anthropic/claude-sonnet-5.5` | 2.00 / 10.00 | |
| Brain | `openai/gpt-6-sol`, `gpt-6.1-sol` | 2.00 / 10.00 | |
| Brain | `google/gemini-4-argon` | 2.00 / 10.00 | |
| Brain | `anthropic/claude-opus-5.5` | 4.00 / 20.00 | |
| Grounder | `bytedance/ui-tars-1.5-7b` | 0.10 / 0.20 | Parasail only; endpoint lacks `tools` |
| Grounder | `meta/muse-glimmer-30b` | 0.30 / 1.20 | 75.4% ScreenSpot-Pro (BenchLM) |
| Grounder? | `moondream/moondream3.1-9b-a2b` | 0.30 / 2.50 | pointing model; no GUI benchmark found |
| Cheap VL | `qwen/qwen3-vl-8b-instruct` | 0.25 / 0.75 | |
| Cheap VL | `inclusionai/ling-3.0-flash-vl` | 0.06 / 0.18 | |

- Batch variants (for example `claude-haiku-5.5:batch` at 0.05/0.25) exist but do not suit interactive use — [OpenRouter models API](https://openrouter.ai/api/v1/models)
- Haiku 5.5 long-prompt pricing: above 100K tokens it costs $0.50 input / $2.50 output (5x). GPT-6 Luna goes to $0.20/$0.75 above 272K — [aiidelist](https://aiidelist.com/blog/claude-haiku-5-5-benchmarks)
- Holo3.1 (H Company) and UI-Venus / GUI-Owl were **not** in the OpenRouter model list, by keyword search over all 452 models — [OpenRouter models API](https://openrouter.ai/api/v1/models)
- The OpenRouter endpoints API lists one endpoint (Alibaba) for `qwen/qwen3.8-omni-flash`, against about 10 for Haiku 5.5 and 7 for GPT-6 Luna — [OpenRouter endpoints API](https://openrouter.ai/api/v1/models/qwen/qwen3.8-omni-flash/endpoints)

**Latency**
- Artificial Analysis, time to first answer token, **including reasoning**: Claude Haiku 5.5 (low effort) 6.28 s via Anthropic; Haiku 5.5 (medium) 12.63 s; Haiku 5.5 (High) 24.23 s vs Gemini 3.8 Flash (High) 21.42 s; Gemini 3.5 Flash-Lite 6.59 s (7.92 s via AI Studio, measured at 10K input tokens) — [AA Haiku 5.5 low](https://artificialanalysis.ai/models/claude-haiku-5-5-low/providers); [AA Haiku 5.5 medium](https://artificialanalysis.ai/models/claude-haiku-5-5-medium/providers); [AA Haiku vs Gemini 3.8 Flash](https://artificialanalysis.ai/models/comparisons/claude-haiku-5-5-high-vs-gemini-3-8-flash); [AA Gemini 3.5 Flash-Lite](https://artificialanalysis.ai/models/gemini-3-5-flash-lite); [AA Flash-Lite providers](https://artificialanalysis.ai/models/gemini-3-5-flash-lite/providers)
- No Artificial Analysis latency was found for GPT-6 Luna or Qwen3.8-Omni-Flash (search returned none).
- Holo3.1-35B-A3B served locally on DGX Spark (vLLM): 326 tok/s BF16, 404 FP8, 568 NVFP4. Average agent step time fell from 6.8 s to 3.3 s with NVFP4 plus harness optimizations. Self-reported — [H Company newsroom](https://hcompany.ai/newsroom/holo3.1)
- OpenAI says GPT-6.1 Terra (26 Oct 2026, $1/$5) completes computer-use tasks "more than three times faster" than GPT-6 Astra and GPT-6.1 Sol. Vendor claim, via secondary sources — [aicatchup](https://aicatchup.com/news/openai-gpt-6-1-terra); [aimagazine](https://aimagazine.com/news/how-openais-gpt-6-1-terra-is-much-faster-and-50-cheaper)

**Coordinate conventions and prompting**
- **Claude (Haiku/Sonnet/Opus):** "Claude works best with absolute pixel coordinates... Claude does not work well when you ask for normalized coordinates, for example... between 0 and 1000." The coordinates are pixel positions in the image *after Claude resizes it*. Standard tier: max edge 1,568 px and max 1,568 visual tokens (⌈w/28⌉×⌈h/28⌉). High-res tier: 2,576 px / 4,784 tokens. Claude pads to a multiple of 28 on the bottom and right, so rescale by the resized size, not the padded size. Recommended approach: pre-resize the screenshot yourself so coordinates map 1:1, ask explicitly for `[x, y]` in pixels, clamp to the image bounds, and set `"transformations": {"oversized_image": "error"}` to catch silent resizes. For small targets, crop the region and offset the result by the crop origin — [Claude docs: vision coordinates](https://platform.claude.com/docs/en/build-with-claude/vision-coordinates)
- For an iPhone portrait screenshot (1179×2556, 1290×2796 or 1206×2622), the reference resize algorithm gives **about 723×1568 px = 1,456 visual tokens** on the standard tier. That is the edge limit applying to a tall image. (Computed with the doc's reference implementation; same source.)
- Claude computer-use tool: tool_result screenshots over the limit are **rejected**, not resized, so resize in the app first. Anthropic's blog suggests trying 1280×720 as a baseline and putting the text instruction before the image — [Claude docs](https://platform.claude.com/docs/en/build-with-claude/vision-coordinates); [Claude blog: best practices for computer use](https://claude.com/fr/blog/best-practices-for-computer-and-browser-use-with-claude)
- **Gemini computer use:** integer x/y on a **0–999 grid** scaled to screen size. The doc's sample code divides by 1000, so the docs are slightly inconsistent — [Google Cloud docs: computer use](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/computer-use)
- **Qwen3.8-Omni-Flash:** no official grounding prompt format was found. OpenRouter lists "GUI interaction" as a supported use — [OpenRouter page](https://openrouter.ai/qwen/qwen3.8-omni-flash/api); [LiteLLM day-0 post](https://docs.litellm.ai/blog/qwen3_8_omni_flash). Mode observed 0–1000 normalized coordinates returned as arrays (internal observation in the brief).
- **Phi-Ground:** coordinates are relative values scaled by 1000 and rounded — the common convention in GUI grounding work — [Phi-Ground report](https://arxiv.org/html/2507.23779v1)
- **Holo3.1:** single-turn grounding mode that maps a text description to **normalized** screen coordinates. Adds native function calling (near-parity with structured JSON on OSWorld) and mobile support — [The Rundown](https://www.therundown.ai/tools/holo-3); [H Company newsroom](https://hcompany.ai/newsroom/holo3.1)

### Inferences
- The Qwen off-screen taps are almost certainly a unit mismatch: 0–1000 relative values were read as pixels. The fix is a per-model coordinate adapter, e.g. `x_px = x/1000 * W_img` for Qwen/Holo/Phi-style models, `x/999 * W` (or /1000) for Gemini, and identity for Claude on a pre-resized image. It should accept both `[x,y]` arrays and `{x,y}` objects, and clamp to the screen.
- Qwen3.8-Omni-Flash's 4–20 s per step is consistent with it having one provider on OpenRouter. There is no provider failover or load spreading, and an omni model may also emit reasoning by default. Haiku and Luna have many endpoints.
- The Artificial Analysis TTFT figures (6–24 s) include reasoning tokens over a 10K-token input. Mode's measured ~2 s for Haiku implies no or minimal extended thinking. To stay under 3 s, the hands model should run **without extended thinking** and with short outputs (one tool call).
- Keep per-call context under 100K tokens to avoid Haiku 5.5's 5x long-prompt price.

### Gaps
- OpenRouter's API exposed no live latency or throughput numbers (null fields), so per-step latency on OpenRouter for Luna, Flash-Lite, MiMo-Flash and Seed-2.0-mini is unmeasured. Mode should benchmark these itself.
- No confirmed coordinate convention for GPT-6 Luna, MiMo-v2.6-Flash, Seed-2.0-mini or Muse Glimmer 30B.
- No hosted, pay-per-token API for Holo3.1 was found (the newsroom page gives no prices).

---

## 3. Planner + grounder splits: does a strong planner with a small grounder beat one model?

### Takeaway
Yes, on long-horizon mobile tasks. In the independent MobileWorld paper, planner + grounder setups (GPT-5, Gemini-3-Pro or Claude-4.5-Sonnet as planner, UI-Ins-7B as grounder) scored 43.8–51.7% overall, against at most 20.9% for any end-to-end GUI model. The paper did not run a controlled ablation against the same planner grounding on its own, and these are 2025-generation models.

### Cited Findings
- MobileWorld overall success: GPT-5 + UI-Ins-7B **51.7%** (GUI-only 54.0, user-interaction 62.2, MCP 51.6, 27.8 steps); Gemini-3-Pro + UI-Ins-7B 46.3% (GUI-only 55.6, 24.2 steps); Claude-4.5-Sonnet + UI-Ins-7B 43.8% (MCP 50.0). The best end-to-end model, Doubao-1.5-UI-TARS, scored 20.9%, and Qwen3-VL-235B 9.5% — [arXiv 2512.19432](https://arxiv.org/html/2512.19432v1)
- The authors attribute the gap to end-to-end models' weak cross-app reasoning and their lack of support for user interaction and MCP. End-to-end models with few steps "often terminat[e] early due to failure rather than efficient task completion" — [arXiv 2512.19432](https://arxiv.org/html/2512.19432v1)
- The newer generation partly closes the gap within single models: vendor-reported Qwen-UI-Agent reaches 82.1% on GUI-only MobileWorld — [aireiter](https://aireiter.com/blog/qwen-ui-agent-benchmarks-access-guide)
- Holo3.1 is built to run as a fast local executor (5.5 req/min on MacBook M4 Pro Q4, 18.1 req/min on DGX Spark NVFP4) — [H Company newsroom](https://hcompany.ai/newsroom/holo3.1)

### Inferences
- For Mode, the split that matters is planner/judge vs executor, more than planner vs grounder. Haiku 5.5's OSWorld score (72.4%) suggests its pixel grounding is adequate. Its failure mode is judgement (dithering, re-planning, re-tapping). The evidence supports a strong brain at decision points rather than a separate grounding model by default.
- A separate grounder adds a second network round-trip per tap, roughly 0.5–2 s (an estimate, not measured). That conflicts with the under-3 s step target unless the grounder is called only when the hands model is unsure or a tap misses.
- If the iPhone engine (CoreDevice / Device Hub) exposes element frames or an accessibility tree, letting the model choose an element ID (set-of-marks) replaces most grounding outright. This is not covered by the sources above; it is a design inference.

### Gaps
- No 2026 controlled ablation was found with the same planner alone vs planner + grounder.
- No measured cost or latency for a planner + grounder pipeline on hosted APIs.

---

## 4. Computer-use / phone-use APIs: mobile support, cost, latency

### Takeaway
Only Google's Gemini computer-use tool explicitly supports a **mobile** environment, with tap, long-press, drag/swipe, open_app and go_back actions on a 0–999 grid. It covers Gemini 3.5 Flash-Lite and newer at normal token prices, but it is a native Gemini API feature. Anthropic's computer-use tool targets a desktop environment, and its mobile support was not found. OpenAI's computer-use models (GPT-6 Astra, GPT-6.1 Terra at $1/$5) have no documented mobile mode in the sources found. Because Mode already uses OpenRouter function calling, it can reproduce any of these action schemas as its own tools.

### Cited Findings
- **Gemini computer use:** supported models are Gemini 4 Argon, 3.8 / 3.7 / 3.6 / 3.5 Flash, 3.5 Flash-Lite, 3 Flash (preview) and Gemini Robotics ER 2. Enable mobile with `ENVIRONMENT_MOBILE` (default is browser). Mobile actions (3.5 Flash and later): `open_app, click, list_apps, wait, go_back, type, drag_and_drop, long_press, press_key, take_screenshot`. Coordinates are on a 0–999 grid. `excluded_predefined_functions` and custom `function_declarations` (e.g. `yield_to_user`) are supported. Pricing is the same as normal Gemini tokens — [Google Cloud docs](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/computer-use)
- Computer use on the Gemini Developer API has no free tier and is charged as regular tokens — [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing)
- Secondary sources say Google recommends 3.8 Flash and positions Flash-Lite for low latency, that it went GA on 30 Oct 2026, and that the standalone 2.5 Computer Use model was shut down on 28 July 2026. One source claims 3.8 Flash prices double from 1 Jan 2027. All unconfirmed from primary pages — [datastudios](https://www.datastudios.org/post/gemini-api-computer-use-generally-available-interactions-generatecontent); [FourWeekMBA](https://fourweekmba.com/ai-gemini-computer-use-reaches-ga-388-days-after-first-preview/)
- **Anthropic computer use tool:** "screenshot, mouse, and keyboard control of a desktop environment". Screenshots must be pre-resized (oversized tool_result images are rejected), and coordinates are scaled back by the app — [Claude docs](https://platform.claude.com/docs/en/build-with-claude/vision-coordinates)
- **OpenAI:** GPT-6 Astra (3 Sep 2026) is the flagship for computer use (92.7% ScreenSpot-Pro per BenchLM). Its price is reported inconsistently ($10/$50 vs $5/$25). GPT-6.1 Terra (26 Oct 2026) costs $1/$5 and is said to be over 3x faster at computer use. No mobile support was found — [aicatchup](https://aicatchup.com/news/openai-gpt-6-1-terra); [getdeploying](https://getdeploying.com/llms/gpt-6-astra); [BenchLM](https://benchlm.ai/benchmarks/screenspot-pro)
- On OpenRouter, `openai/gpt-6.1-terra` is $1/$5 and `openai/gpt-6-astra` is $10/$50 — [OpenRouter models API](https://openrouter.ai/api/v1/models)
- The 2025 Gemini 2.5 Computer Use launch chart showed it as lowest-latency for browser control. That predates current models — [Google blog](https://blog.google/innovation-and-ai/models-and-research/google-deepmind/gemini-computer-use-model/)

### Inferences
- OpenRouter normalizes to chat completions and function calling, so the provider-native computer-use tools (Gemini `ENVIRONMENT_MOBILE`, Anthropic `computer_20xx`) are probably not reachable through it. This is unverified. Mode's own tap/swipe/type tools play the same role. Copying Gemini's mobile action set and stating coordinate units explicitly in each tool's schema is low-risk.
- A BYOK app tied to OpenRouter should not depend on any provider-native computer-use API.

### Gaps
- Whether OpenRouter passes through Gemini's or Anthropic's computer-use tool types was not verified.
- No independent per-step latency figures for Gemini, Anthropic or OpenAI computer-use on mobile were found.

---

## 5. Recommendation: hands, brain, grounder; cost per step and per task

### Takeaway
Keep **Claude Haiku 5.5 (no extended thinking)** as hands. Use pixel coordinates on a screenshot pre-resized to about 723×1568, which costs about $0.0005 per step. Call a stronger **brain only at decision points**, not every step: task start, a stall or loop detected, an ambiguous choice, and a final check. Sonnet 5.5 costs about $0.012 per call. GPT-6.1 Terra ($1/$5) and Gemini 3.8 Flash ($0.825/$4.125) are the cheaper brain candidates for the bake-off. Do **not** add a grounding model by default. Add one only behind a "low confidence or missed tap" trigger. If Qwen-family models stay in the bake-off, they need a 0–1000→pixel adapter and must be judged on real latency.

### Cited Findings (inputs to the estimate)
- Haiku 5.5 $0.10/$0.50, Sonnet 5.5 $2/$10, GPT-6.1 Terra $1/$5, Gemini 3.8 Flash $0.825/$4.125, Gemini 3.5 Flash-Lite $0.30/$2.50, GPT-6 Luna $0.10/$0.50, Qwen3.8-Omni-Flash $0.15/$0.47, UI-TARS-1.5-7B $0.10/$0.20 — [OpenRouter models API](https://openrouter.ai/api/v1/models)
- An iPhone screenshot resized for Claude is about 1,456 visual tokens — computed from [Claude vision-coordinates reference implementation](https://platform.claude.com/docs/en/build-with-claude/vision-coordinates)
- OSWorld 2.1: Haiku 5.5 72.4% vs Luna 48.9% at the same price (Anthropic-reported) — [aiidelist](https://aiidelist.com/blog/claude-haiku-5-5-benchmarks)
- Planner + executor beat end-to-end by about 2.5x on MobileWorld — [arXiv 2512.19432](https://arxiv.org/html/2512.19432v1)

### Inferences (cost model; all estimates)
Assumptions: about 4,000 input tokens per call (1,456 image + about 2,500 system/history text) and 150 output tokens for a hands step (one tool call) or 400 for a brain call. No prompt-caching discount. Non-Claude image token counts differ by provider, so treat those rows as rough.

| Model (role) | Cost per call | 20-step task if used every step |
|---|---|---|
| Claude Haiku 5.5 (hands) | ≈ $0.0005 | ≈ $0.010 |
| GPT-6 Luna (hands) | ≈ $0.0005 | ≈ $0.010 |
| Qwen3.8-Omni-Flash (hands) | ≈ $0.0007 | ≈ $0.013 |
| Gemini 3.5 Flash-Lite (hands) | ≈ $0.0016 | ≈ $0.032 |
| Gemini 3.8 Flash (judge) | ≈ $0.0050 (400 out) | ≈ $0.10 |
| GPT-6.1 Terra (brain) | ≈ $0.006 | ≈ $0.12 |
| Claude Sonnet 5.5 (brain) | ≈ $0.012 | ≈ $0.24 |
| UI-TARS-1.5-7B (grounder, about 2K in / 30 out) | ≈ $0.0002 | ≈ $0.004 |

- **Recommended setup and cost for a typical task (20 hands steps + 2–3 brain calls):** Haiku $0.010 + Sonnet 2–3 × $0.012 ≈ **$0.035–0.046**, which is in budget. With GPT-6.1 Terra as brain: ≈ $0.022–0.028. A Sonnet-every-step design (~$0.24) is 5–10x over budget.
- **Latency:** Haiku without thinking is about 2 s per step (Mode's measurement), which meets the under-3 s target. Brain calls take longer (thinking models show 6–25 s TTFT on Artificial Analysis), so they should be rare, and ideally they should return a short multi-step plan or a verdict that the hands then follow.
- **Brain triggers that fix Haiku's dithering:** the same screen hash after a tap, more than 2 re-taps on the same target, more than N steps without progress, or a choice among options (compare/confirm). The brain should return a decisive next sub-goal, not a full re-plan.
- **Hands alternatives to bake off:** GPT-6 Luna (same price, much lower OSWorld). MiMo-v2.6-Flash and Seed-2.0-mini (cheap, many or few providers, but no GUI benchmarks found). Gemini 3.5 Flash-Lite (3x the cost, native mobile computer-use training, 0–999 coordinates).
- **Grounder option, only if tap-miss rate is measurably high:** on OpenRouter, UI-TARS-1.5-7B (cheap, older, one provider, no tools param) or Muse Glimmer 30B (75.4% ScreenSpot-Pro, unknown coordinate format). Holo3.1-4B/9B are strong mobile executors (71% AndroidWorld) but are open weights only, which conflicts with zero-setup BYOK. Before any of these, try crop-and-zoom with Haiku (Claude docs recommend cropping for small targets) and element frames from the iPhone engine.
- **Qwen3.8-Omni-Flash:** its benchmark lead is self-reported, and it has a single OpenRouter provider. It needs a coordinate adapter (0–1000→px; accept arrays). Re-test only after adding the adapter and turning off reasoning or extra modalities.

### Gaps
- None of these per-step costs or latencies have been measured on Mode's real iPhone flows. A bake-off over the same 10–20 tasks (success rate, taps-to-success, p50/p95 step latency, $/task) is needed to confirm the choice of brain (Sonnet 5.5 vs GPT-6.1 Terra vs Gemini 3.8 Flash) and hands (Haiku 5.5 vs Luna vs Flash-Lite).
- Prompt-caching discounts on OpenRouter for repeated system prompts could cut input costs substantially but were not researched here.
