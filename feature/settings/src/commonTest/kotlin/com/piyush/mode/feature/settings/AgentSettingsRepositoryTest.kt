package com.piyush.mode.feature.settings

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.feature.settings.data.AgentSettingsRepository
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentSettingsRepositoryTest {
    @Test
    fun startsWithTheDefaults() {
        val repo = AgentSettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        val config = repo.config()
        assertEquals(AgentConfig.DEFAULT_MODEL, config.model)
        assertEquals(AgentConfig.DEFAULT_STRONG_MODEL, config.strongModel)
        assertEquals(30, config.maxSteps)
        assertFalse(repo.preferences.value.hasApiKey)
    }

    @Test
    fun keepsTheNamesExistingInstallsUse() {
        val prefs = InMemoryKeyValueStore().apply {
            values["agent_model"] = "qwen/qwen3.7-flash"
            values["agent_max_steps"] = "12"
        }
        val repo = AgentSettingsRepository(InMemorySecretStore("openrouter_api_key" to "sk-or-1"), prefs)
        assertEquals("qwen/qwen3.7-flash", repo.config().model)
        assertEquals(12, repo.config().maxSteps)
        assertTrue(repo.hasApiKey)
    }

    @Test
    fun blankStrongModelMeansNeverStepUpAndStepsAreClamped() {
        val repo = AgentSettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        repo.strongModel = "  "
        repo.maxSteps = "999"
        assertNull(repo.config().strongModel)
        assertEquals(200, repo.config().maxSteps)
        repo.maxSteps = "lots"
        assertEquals(30, repo.config().maxSteps)
    }

    @Test
    fun theKeyIsTrimmedAndABlankKeyRemovesIt() {
        val secrets = InMemorySecretStore()
        val repo = AgentSettingsRepository(secrets, InMemoryKeyValueStore())
        repo.apiKey = "  sk-or-abc  "
        assertEquals("sk-or-abc", secrets.values["openrouter_api_key"])
        assertTrue(repo.preferences.value.hasApiKey)
        repo.apiKey = ""
        assertFalse(repo.preferences.value.hasApiKey)
    }
}
