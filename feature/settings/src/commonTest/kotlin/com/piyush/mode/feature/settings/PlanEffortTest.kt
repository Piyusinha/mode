package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.ModelProvider
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
        assertFalse(ProviderModels.accepts(ModelProvider.ClaudeOAuth, "claude-sonnet-5-5@turbo"))
        assertFalse(ProviderModels.supportsEffort(ModelProvider.OpenRouter))
        assertFalse(ProviderModels.accepts(ModelProvider.OpenRouter, "anthropic/claude-haiku-5.5@low"), "OpenRouter models take no effort")
    }

    @Test
    fun theEffortShownIsTheOneUsed() {
        assertEquals("low", ProviderModels.effortUsed("claude-haiku-5-5@low"))
        assertEquals("medium", ProviderModels.effortUsed("claude-haiku-5-5"), "Haiku's own default")
        assertEquals("high", ProviderModels.effortUsed("claude-sonnet-5-5"), "Sonnet's own default")
        assertNull(ProviderModels.effortUsed("anthropic/claude-haiku-5.5"), "OpenRouter: not shown")
    }
}
