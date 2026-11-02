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

    /** The efforts [option] takes: what its provider listed, or the common ones when it did not say. */
    public fun efforts(provider: ModelProvider, option: ModelOption?): List<String> =
        if (!supportsEffort(provider)) emptyList() else option?.efforts ?: EFFORTS

    public fun supportsEffort(provider: ModelProvider): Boolean = provider == ModelProvider.ChatGpt || provider == ModelProvider.ClaudeOAuth

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
    public fun chatGpt(known: List<String>, info: List<PlanModel> = emptyList()): List<ModelOption> {
        val ids = known.filter { accepts(ModelProvider.ChatGpt, it) }.ifEmpty { listOf(CHATGPT_MAIN, CHATGPT_STRONG) }
        val about = info.associateBy { it.id }
        return ids.map { id ->
            ModelOption(
                id, about[id]?.name ?: displayName(id), 0.0,
                when (id) { CHATGPT_MAIN -> ModelTag.Recommended; CHATGPT_STRONG -> ModelTag.Strongest; else -> null },
                efforts = about[id]?.efforts,
            )
        }.sortedBy { if (it.tag != null) 0 else 1 }
    }

    /**
     * Every model the user's Claude account offers ([known], as Anthropic listed it, newest first), with its
     * name and efforts; Mode's two defaults until the account has been asked.
     */
    public fun claude(known: List<PlanModel> = emptyList()): List<ModelOption> {
        val models = known.filter { accepts(ModelProvider.ClaudeOAuth, it.id) }.ifEmpty {
            listOf(PlanModel(ClaudeOAuthClient.MAIN_MODEL), PlanModel(ClaudeOAuthClient.STRONG_MODEL))
        }
        return models.map { m ->
            ModelOption(
                m.id, m.name ?: claudeName(m.id), 0.0,
                when (m.id) { ClaudeOAuthClient.MAIN_MODEL -> ModelTag.Recommended; ClaudeOAuthClient.STRONG_MODEL -> ModelTag.Strongest; else -> null },
                efforts = m.efforts,
            )
        }
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
