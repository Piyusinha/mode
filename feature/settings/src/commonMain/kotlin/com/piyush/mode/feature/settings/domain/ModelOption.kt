package com.piyush.mode.feature.settings.domain

public enum class ModelTag { Recommended, Cheapest, Strongest }

/** A model the user can pick: it takes screenshots and calls tools. */
public data class ModelOption(
    val id: String,
    /** A readable name, such as "Claude Haiku 5.5". */
    val name: String,
    /** Roughly what a typical task (about 10 steps) costs with it, in US dollars. */
    val costPerTaskUsd: Double,
    val tag: ModelTag? = null,
    /** One line on what it's good at, for picked models. */
    val note: String = "",
    /** Plan models: the efforts the provider says it takes (empty: none), or null when it did not say. */
    val efforts: List<String>? = null,
    /** Billed to the account's API credits, not the plan (Claude Fable). */
    val usesApiCredits: Boolean = false,
    /** An extra allowance after the plan's own runs out (ChatGPT's Luna Reserve), not a model to compare. */
    val reserve: Boolean = false,
)

/** Where the list came from. */
public enum class CatalogSource { Live, Cached, BuiltIn }

public data class CatalogState(
    /** The hand-picked models, in order, with live prices when known. */
    val recommended: List<ModelOption>,
    /** Every usable model, cheapest task first. */
    val all: List<ModelOption>,
    val source: CatalogSource,
    /** When the list was fetched (epoch milliseconds); null for the built-in list. */
    val fetchedAt: Long?,
)
