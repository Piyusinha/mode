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
        )
        assertEquals(listOf("claude-opus-5-5", "claude-3-5-haiku-20241022"), list.map { it.id })
        assertEquals("Claude 3.5 Haiku", list[1].name, "named from its id when Anthropic gave no name")
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), ProviderModels.efforts(ModelProvider.ClaudeOAuth, list[0]))
        assertEquals(ProviderModels.EFFORTS, ProviderModels.efforts(ModelProvider.ClaudeOAuth, list[1]), "not said: the common ones")
        assertEquals(listOf("claude-haiku-5-5", "claude-sonnet-5-5"), ProviderModels.claude().map { it.id }, "before the account is asked")
        assertEquals("Claude Sonnet 5.5", ProviderModels.claudeName("claude-sonnet-5-5"))
    }

    @Test
    fun theEffortShownIsTheOneUsed() {
        assertEquals("low", ProviderModels.effortUsed("claude-haiku-5-5@low"))
        assertEquals("medium", ProviderModels.effortUsed("claude-haiku-5-5"), "Haiku's own default")
        assertEquals("high", ProviderModels.effortUsed("claude-sonnet-5-5"), "Sonnet's own default")
        assertNull(ProviderModels.effortUsed("anthropic/claude-haiku-5.5"), "OpenRouter: not shown")
    }
}
