package com.piyush.mode.agent

import com.piyush.mode.agent.StuckDetector.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StuckDetectorTest {
    private fun StuckDetector.unchangedTimes(n: Int): List<Verdict> = List(n) { looked("same", changed = false) }

    @Test
    fun twoUnchangedStepUpThenTheMainModelGetsTwoMoreTries() {
        val d = StuckDetector(canStepUp = true)
        assertIs<Verdict.Fine>(d.looked("same", changed = null))
        val v = d.unchangedTimes(3)
        assertIs<Verdict.Fine>(v[0])
        assertIs<Verdict.StepUp>(v[1])
        assertIs<Verdict.Fine>(v[2], "counted again from zero after the step-up")
    }

    @Test
    fun fourUnchangedAfterAStepUpStops() {
        val d = StuckDetector(canStepUp = true)
        val v = d.unchangedTimes(4)
        val stop = assertIs<Verdict.Stop>(v[3])
        assertTrue("stopped changing after 4 tries" in stop.why)
    }

    @Test
    fun withoutAStrongModelItStopsAtFourUnchanged() {
        val d = StuckDetector(canStepUp = false)
        val v = d.unchangedTimes(4)
        assertEquals(listOf(true, true, true), v.take(3).map { it is Verdict.Fine })
        assertIs<Verdict.Stop>(v[3])
    }

    @Test
    fun goingRoundInCirclesIsCalledOutThenStopped() {
        val d = StuckDetector(canStepUp = true)
        val v = List(12) { i -> d.looked(if (i % 2 == 0) "A" else "B", changed = true) }
        val warn = assertIs<Verdict.StepUp>(v[6], "A seen for the 4th time")
        assertTrue("4 times" in warn.note!!)
        assertIs<Verdict.Stop>(v.first { it is Verdict.Stop })
        assertEquals(10, v.indexOfFirst { it is Verdict.Stop }, "A seen for the 6th time")
    }

    @Test
    fun screensSeenWithoutAPhoneActionDoNotCountAsVisits() {
        val d = StuckDetector(canStepUp = true)
        repeat(8) { assertIs<Verdict.Fine>(d.looked("plan screen", changed = null)) }
    }

    @Test
    fun aChangingScreenResetsTheCount() {
        val d = StuckDetector(canStepUp = true)
        d.unchangedTimes(1)
        assertIs<Verdict.Fine>(d.looked("next", changed = true))
        assertIs<Verdict.Fine>(d.looked("next", changed = false))
    }

    @Test
    fun threeWaitsInARowAreThreeStepsWithoutProgress() {
        val d = StuckDetector(canStepUp = true)
        repeat(3) { d.waited() }
        // Three steps without progress: the brain decides (or, without one, the strong model steps in).
        assertIs<Verdict.Decide>(d.looked("loading", changed = null))
    }

    @Test
    fun sixStepsWithoutProgressStop() {
        val d = StuckDetector(canStepUp = true)
        repeat(6) { d.stalled() }
        assertIs<Verdict.Stop>(d.looked("same", changed = null))
        d.progressed()
        assertIs<Verdict.Fine>(d.looked("same", changed = null), "progress resets the meter")
    }
}
