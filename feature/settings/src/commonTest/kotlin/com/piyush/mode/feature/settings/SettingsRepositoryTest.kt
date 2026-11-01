package com.piyush.mode.feature.settings

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.RiskConfirmation
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    @Test
    fun startsWithTheAgreedDefaults() {
        val s = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore()).settings.value
        assertEquals(AgentConfig.DEFAULT_MODEL, s.model)
        assertEquals(AgentConfig.DEFAULT_STRONG_MODEL, s.strongModel)
        assertEquals(30, s.maxSteps)
        assertEquals(0.05, s.maxCostPerTaskUsd)
        assertNull(s.dailyCapUsd)
        assertTrue(s.learnFromTasks)
        assertEquals(RiskConfirmation.OnlyVeryRisky, s.askBeforeRisky)
        assertFalse(s.hasApiKey)
    }

    @Test
    fun readsWhatTheFirstVersionStored() {
        val prefs = InMemoryKeyValueStore().apply {
            values["agent_model"] = "qwen/qwen3.7-flash"
            values["agent_strong_model"] = ""
            values["agent_max_steps"] = " 12 "
        }
        val repo = SettingsRepository(InMemorySecretStore("openrouter_api_key" to "sk-or-1"), prefs)
        val config = repo.agentConfig()
        assertEquals("qwen/qwen3.7-flash", config.model)
        assertNull(config.strongModel, "empty meant never step up")
        assertEquals(12, config.maxSteps)
        assertTrue(repo.settings.value.hasApiKey)
    }

    @Test
    fun valuesAreBroughtIntoRangeAndSurviveARestart() {
        val prefs = InMemoryKeyValueStore()
        val repo = SettingsRepository(InMemorySecretStore(), prefs)
        repo.update { it.copy(maxSteps = 999, maxCostPerTaskUsd = 0.0001, dailyCapUsd = 500.0, model = "  ", userName = "  Piyush ") }
        val s = SettingsRepository(InMemorySecretStore(), prefs).settings.value
        assertEquals(200, s.maxSteps)
        assertEquals(0.005, s.maxCostPerTaskUsd)
        assertEquals(100.0, s.dailyCapUsd)
        assertEquals(AgentConfig.DEFAULT_MODEL, s.model)
        assertEquals("Piyush", s.userName)
    }

    @Test
    fun theCostLimitReachesTheAgent() {
        val repo = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore())
        repo.update { it.copy(maxCostPerTaskUsd = 0.02) }
        assertEquals(0.02, repo.agentConfig().maxCostUsd)
    }

    @Test
    fun theKeyIsTrimmedAndABlankKeyRemovesIt() {
        val secrets = InMemorySecretStore()
        val repo = SettingsRepository(secrets, InMemoryKeyValueStore())
        repo.apiKey = "  sk-or-abc  "
        assertEquals("sk-or-abc", secrets.values["openrouter_api_key"])
        assertTrue(repo.settings.value.hasApiKey)
        repo.apiKey = ""
        assertFalse(repo.settings.value.hasApiKey)
    }
}
