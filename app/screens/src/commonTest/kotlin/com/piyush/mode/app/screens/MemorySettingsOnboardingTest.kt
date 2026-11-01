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
        val s = p.state.awaitState { it.apps.isNotEmpty() }
        assertEquals(listOf("Sonali"), s.aboutYou.map { it.title })
        assertEquals("Settings", s.routines.single().name)
        assertEquals(listOf("Settings"), s.routines.single().routines.single().steps)
        assertEquals(1, s.routineCount)
        assertEquals(1, s.tipCount)

        p.delete(s.aboutYou.single().id)
        assertTrue(p.state.awaitState { it.aboutYou.isEmpty() }.aboutYou.isEmpty())
        p.setLearn(false)
        assertFalse(p.state.awaitState { !it.learn }.learn)
        p.forgetEverything()
        assertTrue(p.state.awaitState { it.isEmpty }.isEmpty)
    }

    @Test
    fun tipsAreGroupedByAppHoweverTheAppWasStored() = runTest {
        val f = Fixture(this)
        f.memory.save(MemoryDraft(MemoryKind.Tip, "myfitnesspal", "MyFitnessPal: check the diary first", "Use the day arrows at the top.", "e", app = "MyFitnessPal"))
        f.memory.save(MemoryDraft(MemoryKind.Tip, "myfitnesspal", "MyFitnessPal: don't scroll back and forth", "Read the diary once, top to bottom.", "e"))
        f.memory.save(MemoryDraft(MemoryKind.Tip, "rapido", "Rapido: pickup is your location", "Rapido fills the pickup with where you are.", "e"))
        f.memory.save(MemoryDraft(MemoryKind.Skill, "log a food", "Log a food", "1. Open MyFitnessPal\n2. Tap Log beside the meal\nIf it goes wrong: search again", "e", app = "MyFitnessPal"))
        val p = MemoryPresenter(backgroundScope, f.memory, f.settings, f.clock)
        val s = p.state.awaitState { it.apps.size == 2 }
        assertEquals(listOf("MyFitnessPal", "Rapido"), s.apps.map { it.name }, "one group per app, the most tips first")
        val mfp = s.app("MyFitnessPal")!!
        assertEquals(setOf("Check the diary first", "Don't scroll back and forth"), mfp.tips.map { it.title }.toSet(), "no app name in front")
        val routine = mfp.routines.single()
        assertEquals(listOf("Open MyFitnessPal", "Tap Log beside the meal"), routine.steps)
        assertEquals("If it goes wrong: search again", routine.body)
        assertEquals("Rapido", s.apps.last().name, "named from the title when the app was not stored")
    }

    @Test
    fun anItemsTitleAndTextCanBothBeEdited() = runTest {
        val f = Fixture(this)
        f.memory.save(MemoryDraft(MemoryKind.Tip, "rapido", "Rapido: pickup", "Pickup is your location.", "e"))
        val p = MemoryPresenter(backgroundScope, f.memory, f.settings, f.clock)
        val tip = p.state.awaitState { it.apps.isNotEmpty() }.apps.single().tips.single()
        p.edit(tip.id, "Recent destinations are on the home screen.", title = "Recent destinations")
        val edited = p.state.awaitState { st -> st.apps.single().tips.single().title == "Recent destinations" }.apps.single().tips.single()
        assertEquals("Recent destinations are on the home screen.", edited.text)
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
}
