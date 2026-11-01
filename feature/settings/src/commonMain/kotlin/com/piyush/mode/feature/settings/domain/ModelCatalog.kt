package com.piyush.mode.feature.settings.domain

/** A model the user can pick, with a short note on price or strength. */
public data class ModelOption(val id: String, val note: String)

/**
 * OpenRouter models worth trying on a phone: all take images and tool calls.
 * Prices are $ per million tokens (in · out) on 2026-11-01; a step is about
 * 3.5K tokens in and 70 out.
 */
public object ModelCatalog {
    public val recommended: List<ModelOption> = listOf(
        ModelOption("anthropic/claude-haiku-5.5", "0.10 · 0.50 — default; reported 72% on OSWorld 2.1"),
        ModelOption("qwen/qwen3.8-27b", "0.04 · 2.30 — top cheap model on OSWorld-Verified (0.84)"),
        ModelOption("qwen/qwen3.7-flash", "0.03 · 0.13 — cheapest"),
        ModelOption("openai/gpt-5-nano", "0.05 · 0.40"),
        ModelOption("qwen/qwen3.8-flash", "0.15 · 0.47"),
        ModelOption("anthropic/claude-sonnet-5.5", "2.00 · 10.00 — stronger model when stuck"),
    )
}
