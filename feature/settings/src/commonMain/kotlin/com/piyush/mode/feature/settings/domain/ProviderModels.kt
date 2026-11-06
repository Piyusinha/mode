package com.piyush.mode.feature.settings.domain

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.llm.ClaudeOAuthClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.PlanModel

/** Provider-specific defaults/catalogue metadata live here, not in UI or the task runner. */
public object ProviderModels {
    public fun main(provider: ModelProvider): String = when (provider) {
        ModelProvider.OpenRouter -> AgentConfig.DEFAULT_MODEL
        ModelProvider.ClaudeOAuth -> ClaudeOAuthClient.MAIN_MODEL
        ModelProvider.ChatGpt -> CHATGPT_MAIN
    }

    public fun strong(provider: ModelProvider): String = when (provider) {
        ModelProvider.OpenRouter -> AgentConfig.DEFAULT_STRONG_MODEL
        ModelProvider.ClaudeOAuth -> ClaudeOAuthClient.STRONG_MODEL
        ModelProvider.ChatGpt -> CHATGPT_STRONG
    }

    public fun accepts(provider: ModelProvider, model: String): Boolean {
        val base = base(model)
        // Only plan models carry an effort ("gpt-6.1-terra@low", "claude-opus-5-5@max"): any the provider listed.
        if (base != model && (!supportsEffort(provider) || effort(model)?.all(Char::isLetter) != true)) return false
        return when (provider) {
            ModelProvider.OpenRouter -> '/' in base && !base.startsWith("claude-oauth/")
            ModelProvider.ClaudeOAuth -> base.startsWith("claude-") && '/' !in base
            ModelProvider.ChatGpt -> '/' !in base && !base.startsWith("claude-") && base.isNotBlank()
        }
    }

    /** Offered for a plan model when its provider does not say which efforts it takes. */
    public val EFFORTS: List<String> = PlanModel.COMMON

    /** The highest effort Mode offers: above it a phone step waits too long for too little. */
    public const val MAX_EFFORT: String = "high"

    /**
     * The efforts [option] takes, up to [MAX_EFFORT]: what its provider listed, or the common ones when it
     * did not say.
     */
    public fun efforts(provider: ModelProvider, option: ModelOption?): List<String> {
        if (!supportsEffort(provider)) return emptyList()
        val top = PlanModel.LADDER.indexOf(MAX_EFFORT)
        return (option?.efforts ?: EFFORTS).filter { PlanModel.LADDER.indexOf(it) in 0..top }
    }

    public fun supportsEffort(provider: ModelProvider): Boolean = provider == ModelProvider.ChatGpt || provider == ModelProvider.ClaudeOAuth

    /**
     * The phone-control model at its lowest effort, for one quick spoken line (an acknowledgement, an update):
     * no thinking needed, so it comes back fast and costs little. [known] lists the plan's models with their
     * efforts; "low" when the plan doesn't say. Null when no model is chosen.
     */
    public fun quick(model: String?, provider: ModelProvider, known: List<com.piyush.mode.llm.PlanModel>): Pair<String, ModelProvider>? {
        val id = model ?: return null
        val effort = if (supportsEffort(provider)) {
            known.firstOrNull { it.id == base(id) }?.efforts?.let { com.piyush.mode.llm.PlanModel.ordered(it).firstOrNull() } ?: "low"
        } else null
        return withEffort(id, effort) to provider
    }

    /**
     * The provider's fast, light model at its lowest effort, for short spoken lines (the greeting, the reply
     * to a task, progress updates): the newest Luna on a ChatGPT plan, the newest Haiku on a Claude account.
     * On OpenRouter, or before the plan's list is known, the phone-control [model] at its lowest effort.
     */
    public fun fast(model: String?, provider: ModelProvider, known: List<com.piyush.mode.llm.PlanModel>): Pair<String, ModelProvider>? {
        val family = when (provider) {
            ModelProvider.ChatGpt -> FAST_CHATGPT
            ModelProvider.ClaudeOAuth -> FAST_CLAUDE
            ModelProvider.OpenRouter -> null
        }
        val pick = family?.let { f ->
            known.map { it.id }.distinct()
                .filter { accepts(provider, it) && !isReserve(it) && CHATGPT_HIDDEN.none { h -> h in it } && familyAndVersion(provider, it).first == f }
                .maxByOrNull { familyAndVersion(provider, it).second ?: 0.0 }
        } ?: if (provider == ModelProvider.ClaudeOAuth) ClaudeOAuthClient.MAIN_MODEL else null
        return quick(pick ?: model, provider, known)
    }

    /** The light, fast family on each plan: ChatGPT's Luna (Sol is the strongest, Terra the middle), Claude's Haiku. */
    private const val FAST_CHATGPT = "luna"
    private const val FAST_CLAUDE = "haiku"

    /** The model without its effort: "gpt-6.1-terra@low" → "gpt-6.1-terra". */
    public fun base(id: String): String = id.substringBefore('@')

    public fun effort(id: String): String? = id.substringAfter('@', "").ifEmpty { null }

    /**
     * The effort [id] runs at: the one chosen, or, with none, the model's own default where it is known
     * (Claude Haiku 5.5 runs at medium and Sonnet 5.5 at high when no effort is sent).
     */
    public fun effortUsed(id: String): String? = effort(id) ?: when {
        base(id).startsWith("claude-haiku-") -> "medium"
        base(id).startsWith("claude-sonnet-") -> "high"
        else -> null
    }

    public fun withEffort(id: String, effort: String?): String = base(id) + (effort?.let { "@$it" } ?: "")

    /** The defaults until the plan's own list is known (OpenAI names them; the list replaces these). */
    public const val CHATGPT_MAIN: String = "gpt-6.1-terra"
    public const val CHATGPT_STRONG: String = "gpt-6.1-sol"

    /**
     * The models on the user's ChatGPT plan ([known], from the account), or the defaults when the plan
     * has not been asked yet. The defaults are marked; the rest keep the plan's order.
     */
    public fun chatGpt(known: List<String>, info: List<PlanModel> = emptyList(), keep: Set<String> = emptySet()): List<ModelOption> {
        val ids = known.filter {
            accepts(ModelProvider.ChatGpt, it) && CHATGPT_HIDDEN.none { h -> h in it } &&
                (base(it) in keep || worthListing(ModelProvider.ChatGpt, it, known))
        }
            .ifEmpty { listOf(CHATGPT_MAIN, CHATGPT_STRONG) }
        val about = info.associateBy { it.id }
        return ids.map { id ->
            ModelOption(
                id, CHATGPT_NAMES[id] ?: about[id]?.name ?: displayName(id), 0.0,
                when (id) { CHATGPT_MAIN -> ModelTag.Recommended; CHATGPT_STRONG -> ModelTag.Strongest; else -> null },
                efforts = about[id]?.efforts,
                reserve = isReserve(id),
            )
        }.sortedBy { if (it.tag != null) 0 else 1 }
    }

    /**
     * Every model the user's Claude account offers ([known], as Anthropic listed it, newest first), with its
     * name and efforts; Mode's two defaults until the account has been asked.
     */
    public fun claude(known: List<PlanModel> = emptyList(), keep: Set<String> = emptySet()): List<ModelOption> {
        val ids = known.map { it.id }
        val models = known.filter { accepts(ModelProvider.ClaudeOAuth, it.id) && (it.id in keep || worthListing(ModelProvider.ClaudeOAuth, it.id, ids)) }.ifEmpty {
            listOf(PlanModel(ClaudeOAuthClient.MAIN_MODEL), PlanModel(ClaudeOAuthClient.STRONG_MODEL))
        }
        return models.map { m ->
            ModelOption(
                m.id, m.name ?: claudeName(m.id), 0.0,
                when (m.id) { ClaudeOAuthClient.MAIN_MODEL -> ModelTag.Recommended; ClaudeOAuthClient.STRONG_MODEL -> ModelTag.Strongest; else -> null },
                efforts = m.efforts,
                usesApiCredits = usesApiCredits(m.id),
            )
        }
    }

    /** ChatGPT entries that are not models to pick for Mode (Codex's own reviewer). Hidden even if one was in use. */
    private val CHATGPT_HIDDEN = listOf("auto-review", "codex-auto-review")

    /**
     * ChatGPT's Luna Reserve: extra Luna usage after the plan's own allowance runs out, with its own limit
     * (help.openai.com, "Luna Reserve in Codex and ChatGPT work"). Listed with what it is, never compared.
     */
    private val CHATGPT_RESERVE = setOf("gpt-reserve")
    private val CHATGPT_NAMES = mapOf("gpt-reserve" to "Luna Reserve")

    public fun isReserve(id: String): Boolean = base(id) in CHATGPT_RESERVE

    /** Below this, older models only fill the list: the newer ones do the same job better. */
    public const val CHATGPT_MIN_VERSION: Double = 6.1
    /** Older ChatGPT models still worth listing (OpenAI's own pick for quick work). */
    private val CHATGPT_OLDER_KEPT = setOf("astra")
    public const val CLAUDE_MIN_VERSION: Double = 5.5

    /**
     * Claude families billed to the account's API credits, not the Claude plan. Anthropic's model list does
     * not say so, so it is named here; the newest of each is listed whatever its number.
     */
    private val CLAUDE_API_CREDIT_FAMILIES = setOf("fable")

    /** True for a Claude model that spends API credits rather than the plan's allowance. */
    public fun usesApiCredits(id: String): Boolean = familyAndVersion(ModelProvider.ClaudeOAuth, id).first in CLAUDE_API_CREDIT_FAMILIES

    /**
     * Whether [id] is worth listing among [all]: current models (ChatGPT 6.1 and later, Claude 5.5 and
     * later), the older ones kept on purpose, the newest of an API-credit family, and any id without a
     * version (its age is unknown, so it is not hidden).
     */
    internal fun worthListing(provider: ModelProvider, id: String, all: List<String>): Boolean {
        val (family, version) = familyAndVersion(provider, id)
        if (version == null) return true
        return when (provider) {
            ModelProvider.ChatGpt -> version >= CHATGPT_MIN_VERSION || family in CHATGPT_OLDER_KEPT
            ModelProvider.ClaudeOAuth -> if (family in CLAUDE_API_CREDIT_FAMILIES) {
                all.none { other -> familyAndVersion(provider, other).let { (f, v) -> f == family && v != null && v > version } }
            } else version >= CLAUDE_MIN_VERSION
            ModelProvider.OpenRouter -> true
        }
    }

    /**
     * A plan model's family and version: "claude-opus-5-5" → (opus, 5.5), "claude-3-5-haiku-20241022" →
     * (haiku, 3.5), "gpt-6.1-terra-mini" → (terra mini, 6.1), "gpt-reserve" → (reserve, null).
     */
    internal fun familyAndVersion(provider: ModelProvider, id: String): Pair<String?, Double?> {
        val parts = base(id).lowercase().split('-').drop(1).filterNot { it.length == 8 && it.all(Char::isDigit) }
        val family = parts.filter { p -> p.any(Char::isLetter) }.joinToString(" ").ifEmpty { null }
        val version = if (provider == ModelProvider.ChatGpt) {
            parts.firstOrNull { Regex("""\d+(\.\d+)?""").matches(it) }?.toDoubleOrNull()
        } else {
            parts.filter { p -> p.all(Char::isDigit) }.take(2).takeIf { it.isNotEmpty() }?.joinToString(".")?.toDoubleOrNull()
        }
        return family to version
    }

    /** "claude-sonnet-5-5" → "Claude Sonnet 5.5"; "claude-3-5-haiku-20241022" → "Claude 3.5 Haiku". */
    public fun claudeName(id: String): String {
        val words = mutableListOf<String>()
        base(id).split('-').filterNot { it.length == 8 && it.all(Char::isDigit) }.forEach { part ->
            val last = words.lastOrNull()
            if (part.all(Char::isDigit) && last != null && last.first().isDigit()) words[words.lastIndex] = "$last.$part"
            else words += part.replaceFirstChar { it.uppercase() }
        }
        return words.joinToString(" ")
    }

    /** "gpt-6.1-terra" → "GPT-6.1 Terra". */
    public fun displayName(id: String): String = id.split('-').joinToString(" ") { part ->
        if (part.equals("gpt", true)) "GPT" else part.replaceFirstChar { it.uppercase() }
    }.replace(Regex("""^GPT (\d)"""), "GPT-$1")

}
