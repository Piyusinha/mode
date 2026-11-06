package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.PlanModel
import kotlin.test.Test
import kotlin.test.assertEquals

class FastModelTest {
    private val chatGptPlan = listOf(
        PlanModel("codex-auto-review"), PlanModel("gpt-5.6-luna", efforts = listOf("low", "medium")),
        PlanModel("gpt-6-luna", efforts = listOf("minimal", "low", "medium")), PlanModel("gpt-6.1-sol", efforts = listOf("low", "high")),
        PlanModel("gpt-6.1-terra", efforts = listOf("low", "medium")), PlanModel("gpt-reserve"),
    )

    @Test
    fun onChatGptSpokenLinesUseTheNewestLunaAtItsLowestEffort() {
        assertEquals("gpt-6-luna@minimal" to ModelProvider.ChatGpt, ProviderModels.fast("gpt-6.1-terra", ModelProvider.ChatGpt, chatGptPlan))
    }

    @Test
    fun onClaudeSpokenLinesUseTheNewestHaiku() {
        val claude = listOf(PlanModel("claude-opus-5-5"), PlanModel("claude-sonnet-5-5"), PlanModel("claude-haiku-5-5"), PlanModel("claude-3-5-haiku-20241022"))
        assertEquals("claude-haiku-5-5", ProviderModels.fast("claude-sonnet-5-5", ModelProvider.ClaudeOAuth, claude)!!.first.substringBefore('@'))
        assertEquals("claude-haiku-5-5", ProviderModels.fast("claude-sonnet-5-5", ModelProvider.ClaudeOAuth, emptyList())!!.first.substringBefore('@'),
            "before the account's list is known: Mode's Haiku")
    }

    @Test
    fun onOpenRouterOrBeforeThePlanIsKnownItIsThePhoneControlModel() {
        assertEquals("anthropic/claude-haiku-5.5", ProviderModels.fast("anthropic/claude-haiku-5.5", ModelProvider.OpenRouter, chatGptPlan)!!.first)
        assertEquals("gpt-6.1-terra", ProviderModels.fast("gpt-6.1-terra", ModelProvider.ChatGpt, emptyList())!!.first.substringBefore('@'))
    }
}
