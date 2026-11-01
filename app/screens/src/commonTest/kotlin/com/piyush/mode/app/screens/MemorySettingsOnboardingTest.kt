package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.memory.MemoryPresenter
import com.piyush.mode.app.screens.onboarding.OnboardingPresenter
import com.piyush.mode.app.screens.onboarding.OnboardingStep
import com.piyush.mode.app.screens.settings.SettingsPresenter
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemorySettingsOnboardingTest {
    @Test
    fun memoryIsGroupedByKindAndCanBeForgotten() = runTest {
        val f = Fixture(this)
        f.memory.save(MemoryDraft(MemoryKind.Fact, "sonali", "Sonali", "Sonali: WhatsApp", "e"))
        f.memory.save(MemoryDraft(MemoryKind.Skill, "turn off dark mode", "Turn off Dark Mode", "1. Settings", "e", app = "Settings"))
        f.memory.save(MemoryDraft(MemoryKind.Tip, "calendar", "Calendar", "list button top right", "e", app = "Calendar"))
        val p = MemoryPresenter(backgroundScope, f.memory, f.settings, f.clock)
        val s = p.state.awaitState { it.tips.isNotEmpty() }
        assertEquals(listOf("Sonali"), s.aboutYou.map { it.title })
        assertEquals("Settings", s.skills.single().app)

        p.delete(s.aboutYou.single().id)
        assertTrue(p.state.awaitState { it.aboutYou.isEmpty() }.aboutYou.isEmpty())
        p.setLearn(false)
        assertFalse(p.state.awaitState { !it.learn }.learn)
        p.forgetEverything()
        assertTrue(p.state.awaitState { it.isEmpty }.isEmpty)
    }

    @Test
    fun settingsPickModelsFromTheListAndKeepLimits() = runTest {
        val f = Fixture(this)
        val p = SettingsPresenter(backgroundScope, f.settings, f.catalog)
        val s = p.state.awaitState { it.picks.isNotEmpty() }
        val cheapest = s.picks.last { it.tag == com.piyush.mode.feature.settings.domain.ModelTag.Cheapest }
        p.chooseModel(cheapest.id)
        p.chooseStuckModel(null)
        p.setMaxCostPerTask(0.02)
        p.setDailyCap(1.0)
        val after = p.state.awaitState { it.model?.id == cheapest.id && it.stuckModel == null }
        assertTrue(after.picks.single { it.id == cheapest.id }.isMain)
        assertEquals(0.02, after.maxCostPerTaskUsd)
        assertEquals(1.0, after.dailyCapUsd)

        p.search("qwen")
        assertTrue(p.state.awaitState { it.results.isNotEmpty() }.results.all { "qwen" in it.id.lowercase() || "qwen" in it.name.lowercase() })
        p.setKey("sk-or-v1-abcdefghijklmnopqrstuvwxyz1234")
        assertEquals("1234", p.state.awaitState { it.keyEnding == "1234" }.keyEnding)
    }

    private fun TestScope.onboarding(f: Fixture) = OnboardingPresenter(backgroundScope, f.settings, f.catalog, f.session, f.runner, f.handsCheck)

    @Test
    fun onboardingWalksTheStepsAndSavesEachAnswer() = runTest {
        val f = Fixture(this)
        f.settings.apiKey = ""
        val p = onboarding(f)
        assertEquals(OnboardingStep.Welcome, p.state.value.step)
        p.next()
        assertFalse(p.state.value.canContinue, "a name is needed")
        p.setName("Piyush")
        p.next()
        assertEquals("Piyush", f.settings.settings.value.userName)
        p.setKey("not a key")
        assertFalse(p.state.value.canContinue)
        p.setKey("sk-or-v1-abcdefghijklmnopqrstuvwxyz")
        p.next()
        assertTrue(f.settings.settings.value.hasApiKey)
        val model = p.state.awaitState { it.models.isNotEmpty() }.models.first().id
        p.chooseModel(model)
        p.next()
        assertEquals(model, f.settings.settings.value.model)
        assertEquals(OnboardingStep.Permissions, p.state.value.step)

        p.back()
        assertEquals(OnboardingStep.Model, p.state.value.step)
    }

    @Test
    fun theTestRunsTheFixedSetupTaskAndAFailureLeadsToDiagnose() = runTest {
        val f = Fixture(this, ScriptedModel(call("done", "evidence" to "Wi-Fi page", "summary" to "Opened Wi-Fi and came back.")))
        val p = onboarding(f)
        p.connect()
        testScheduler.advanceUntilIdle()
        assertEquals(OnboardingStep.TestRun, p.state.awaitState { it.step == OnboardingStep.TestRun }.step)
        p.runTest()
        testScheduler.advanceUntilIdle()
        val result = p.state.awaitState { it.step == OnboardingStep.TestResult }
        assertEquals("Opened Wi-Fi and came back.", result.testSummary)
        assertEquals(OnboardingPresenter.TEST_TASK, f.runs.runs().first().task)

        p.confirmTest(worked = false)
        assertEquals(OnboardingStep.Diagnose, p.state.value.step)
        assertFalse(f.settings.settings.value.onboardingDone)
        p.finish()
        assertTrue(f.settings.settings.value.onboardingDone)
        assertEquals(OnboardingStep.Finished, p.state.value.step)
    }

    @Test
    fun aKeyAlreadySavedNeedNotBeTypedAgain() = runTest {
        val f = Fixture(this)
        val p = onboarding(f)
        p.next()
        p.setName("Piyush")
        p.next()
        assertEquals(OnboardingStep.Key, p.state.value.step)
        assertTrue(p.state.value.canContinue, "the saved key is kept")
        p.next()
        assertEquals("sk-or-test", f.settings.apiKey)
    }
}
