package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.screens.home.SetupTip
import com.piyush.mode.app.screens.home.SetupTips
import com.piyush.mode.feature.settings.domain.ModeSettings
import com.piyush.mode.feature.settings.domain.VoiceChoice
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupTipsTest {
    private val start = nov(1, 8)
    private val day = 24 * 60 * 60 * 1000L
    private val setUp = ModeSettings(onboardingDone = true, tipsStartedAtMs = start)

    @Test
    fun everyTipShowsEveryDayForFiveDaysThenNone() {
        (0 until 5).forEach { d ->
            val b = SetupTips.banner(setUp, start + d * day + 1000)!!
            assertEquals(SetupTip.entries.toList(), b.tips, "day ${d + 1}: all five")
            assertEquals(SetupTip.Voice, b.showing, "day ${d + 1}: starts at the first")
        }
        assertNull(SetupTips.banner(setUp, start + 5 * day), "day 6: gone by itself")
    }

    @Test
    fun theCloseButtonComesOnDayThree() {
        assertFalse(SetupTips.banner(setUp, start)!!.closable)
        assertFalse(SetupTips.banner(setUp, start + day)!!.closable)
        assertTrue(SetupTips.banner(setUp, start + 2 * day)!!.closable)
        assertTrue(SetupTips.banner(setUp, start + 4 * day)!!.closable)
    }

    @Test
    fun aTipWhoseSettingIsDoneIsSkipped() {
        val voiceSet = setUp.copy(voices = mapOf("elevenlabs" to VoiceChoice("v1", "Rachel")))
        val b = SetupTips.banner(voiceSet, start)!!
        assertEquals(SetupTip.Listening, b.showing, "the voice is set, so it starts at the next tip")
        assertFalse(SetupTip.Voice in b.tips)
        val named = setUp.copy(userName = "Alex")
        assertEquals(SetupTip.entries.toList() - SetupTip.Name, SetupTips.banner(named, start)!!.tips, "a name is set: four tips")
        val allDone = setUp.copy(
            voices = mapOf("elevenlabs" to VoiceChoice("v1", "Rachel")), hearingOrder = listOf("deepgram", "apple", "soniox"),
            fullAccess = true, voiceUnlock = true, userName = "Alex",
        )
        assertNull(SetupTips.banner(allDone, start), "nothing left to tell")
    }

    @Test
    fun noBannerBeforeSetupOrOnceClosed() {
        assertNull(SetupTips.banner(setUp.copy(onboardingDone = false), start))
        assertNull(SetupTips.banner(setUp.copy(tipsStartedAtMs = null), start))
        assertNull(SetupTips.banner(setUp.copy(tipsHidden = true), start))
    }

    @Test
    fun theDotsShowAnotherOpenTip() {
        assertEquals(SetupTip.Access, SetupTips.banner(setUp, start, picked = SetupTip.Access)!!.showing)
        val named = setUp.copy(userName = "Alex")
        assertEquals(SetupTip.Voice, SetupTips.banner(named, start, picked = SetupTip.Name)!!.showing, "a done tip can't be picked")
    }

    private fun TestScope.home(f: Fixture) = HomePresenter(backgroundScope, f.settings, f.runner, f.queue, f.tasks, f.conversations, f.memory, f.clock)

    @Test
    fun homeStartsTheFiveDaysTheFirstTimeItShowsAfterSetup() = runTest {
        val f = Fixture(this)
        f.settings.update { it.copy(onboardingDone = true) }
        val s = home(f).state.awaitState { it.tips != null }
        assertEquals(f.now, f.settings.settings.value.tipsStartedAtMs)
        assertEquals(SetupTip.Voice, s.tips!!.showing)
        assertFalse(s.tips!!.closable)
    }

    @Test
    fun closingHidesTipsForGoodAndUndoBringsThemBack() = runTest {
        val f = Fixture(this)
        f.settings.update { it.copy(onboardingDone = true, tipsStartedAtMs = f.now - 2 * day) }
        val p = home(f)
        assertTrue(p.state.awaitState { it.tips != null }.tips!!.closable)
        p.hideTips()
        p.state.awaitState { it.tips == null }
        assertTrue(f.settings.settings.value.tipsHidden)
        p.undoHideTips()
        assertNotNull(p.state.awaitState { it.tips != null }.tips)
        p.showTip(SetupTip.Name)
        assertEquals(SetupTip.Name, p.state.awaitState { it.tips?.showing == SetupTip.Name }.tips!!.showing)
    }

    @Test
    fun eachTipGoesOnlyWhenItsOwnSettingChanges() {
        fun tips(s: ModeSettings) = SetupTips.banner(s, start)!!.tips

        // Voice: another provider speaking, or a voice picked (even one of Apple's).
        assertFalse(SetupTip.Voice in tips(setUp.copy(speechOrder = listOf("elevenlabs", "apple", "fish"))))
        assertFalse(SetupTip.Voice in tips(setUp.copy(voices = mapOf("apple" to VoiceChoice("ava", "Ava")))))
        assertTrue(SetupTip.Voice in tips(setUp.copy(speechOrder = listOf("apple", "elevenlabs", "fish"))), "reordering below Apple changes nothing")

        // Dictation: another listener in use; the language alone doesn't count.
        assertFalse(SetupTip.Listening in tips(setUp.copy(hearingOrder = listOf("deepgram", "apple", "soniox"))))
        assertTrue(SetupTip.Listening in tips(setUp.copy(language = "hi")))

        // Unlock by voice set up: only that tip goes.
        val unlocked = tips(setUp.copy(voiceUnlock = true))
        assertEquals(SetupTip.entries.toList() - SetupTip.StartWithoutOpening, unlocked)
    }

    @Test
    fun theCloseButtonAndFiveDaysStayWhenSomeTipsAreDone() {
        val some = setUp.copy(voiceUnlock = true, voices = mapOf("apple" to VoiceChoice("ava", "Ava")))
        assertFalse(SetupTips.banner(some, start + day)!!.closable)
        assertTrue(SetupTips.banner(some, start + 2 * day)!!.closable)
        assertNull(SetupTips.banner(some, start + 5 * day))
    }
}
