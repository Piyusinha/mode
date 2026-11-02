package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.PlanModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanEffortTest {
    @Test
    fun claudeModelsTakeAnEffortAsChatGptModelsDo() {
        assertTrue(ProviderModels.supportsEffort(ModelProvider.ClaudeOAuth))
        assertTrue(ProviderModels.accepts(ModelProvider.ClaudeOAuth, "claude-sonnet-5-5@high"))
        assertTrue(ProviderModels.accepts(ModelProvider.ClaudeOAuth, "claude-opus-5-5@max"), "any effort a model lists")
        assertFalse(ProviderModels.accepts(ModelProvider.ClaudeOAuth, "claude-sonnet-5-5@"), "an empty effort is not one")
        assertFalse(ProviderModels.supportsEffort(ModelProvider.OpenRouter))
        assertFalse(ProviderModels.accepts(ModelProvider.OpenRouter, "anthropic/claude-haiku-5.5@low"), "OpenRouter models take no effort")
    }

    @Test
    fun claudesListIsTheAccountsOwnWithReadableNames() {
        val list = ProviderModels.claude(
            listOf(PlanModel("claude-opus-5-5", "Claude Opus 5.5", listOf("low", "medium", "high", "xhigh", "max")), PlanModel("claude-3-5-haiku-20241022")),
            keep = setOf("claude-3-5-haiku-20241022"),
        )
        assertEquals(listOf("claude-opus-5-5", "claude-3-5-haiku-20241022"), list.map { it.id }, "an old model in use stays")
        assertEquals("Claude 3.5 Haiku", list[1].name, "named from its id when Anthropic gave no name")
        assertEquals(listOf("low", "medium", "high"), ProviderModels.efforts(ModelProvider.ClaudeOAuth, list[0]), "up to High")
        assertEquals(ProviderModels.EFFORTS, ProviderModels.efforts(ModelProvider.ClaudeOAuth, list[1]), "not said: the common ones")
        assertEquals(listOf("claude-haiku-5-5", "claude-sonnet-5-5"), ProviderModels.claude().map { it.id }, "before the account is asked")
        assertEquals("Claude Sonnet 5.5", ProviderModels.claudeName("claude-sonnet-5-5"))
    }

    @Test
    fun onlyCurrentModelsAreListedWithTheNewestFable() {
        val listed = listOf(
            "claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5", "claude-fable-5-1", "claude-opus-5", "claude-sonnet-5",
            "claude-fable-5", "claude-opus-4-8", "claude-sonnet-4-6", "claude-opus-4-5-20251101", "claude-haiku-4-5-20251001",
        ).map { PlanModel(it) }
        val shown = ProviderModels.claude(listed)
        assertEquals(listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5", "claude-fable-5-1"), shown.map { it.id })
        assertEquals(listOf("claude-fable-5-1"), shown.filter { it.usesApiCredits }.map { it.id }, "Fable is on API credits")
    }

    @Test
    fun chatGptListsSixPointOneAndLaterAndAstra() {
        val shown = ProviderModels.chatGpt(listOf("gpt-6.1-terra", "gpt-6.2-sol", "gpt-6-luna", "gpt-6-astra", "gpt-5.5", "gpt-reserve"))
        assertEquals(setOf("gpt-6.1-terra", "gpt-6.2-sol", "gpt-6-astra", "gpt-reserve"), shown.map { it.id }.toSet(), "no version: age unknown, kept")
        assertEquals(setOf("gpt-6.1-terra", "gpt-6-luna"), ProviderModels.chatGpt(listOf("gpt-6.1-terra", "gpt-6-luna"), keep = setOf("gpt-6-luna")).map { it.id }.toSet())
        val withReserve = ProviderModels.chatGpt(listOf("gpt-6.1-terra", "gpt-reserve", "codex-auto-review"))
        assertEquals(listOf("gpt-6.1-terra", "gpt-reserve"), withReserve.map { it.id }, "Codex's reviewer is not listed")
        assertEquals("Luna Reserve", withReserve[1].name)
        assertTrue(withReserve[1].reserve && !withReserve[0].reserve)
        assertEquals(
            listOf("gpt-6.1-terra"),
            ProviderModels.chatGpt(listOf("gpt-6.1-terra", "codex-auto-review"), keep = setOf("codex-auto-review")).map { it.id },
            "a reviewer in use is still not listed",
        )
    }

    @Test
    fun theEffortShownIsTheOneUsed() {
        assertEquals("low", ProviderModels.effortUsed("claude-haiku-5-5@low"))
        assertEquals("medium", ProviderModels.effortUsed("claude-haiku-5-5"), "Haiku's own default")
        assertEquals("high", ProviderModels.effortUsed("claude-sonnet-5-5"), "Sonnet's own default")
        assertNull(ProviderModels.effortUsed("anthropic/claude-haiku-5.5"), "OpenRouter: not shown")
    }
}
