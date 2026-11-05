package com.piyush.mode.agent

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoveryLadderTest {
    private val asked = mutableListOf<String>()
    private val brain: suspend (String) -> String = { q -> asked += q; "your planning mind decided: 1. Search for the bakery\n2. Open its page. Do that now" }

    @Test
    fun theRungsComeInOrderAndProgressResetsThem() = runTest {
        val l = RecoveryLadder()
        assertNull(l.climb(1, 1, true, 0, false, "tap \"<\"", "ok", null, brain))
        // The same tap twice with no change: another way, no model call.
        val one = assertNotNull(l.climb(2, 2, false, 0, false, "tap \"<\"", "screen did NOT change", null, brain))
        assertTrue("TRY ANOTHER WAY" in one.note); assertEquals(1, l.tier); assertTrue(asked.isEmpty())
        // A third time: the planning mind names another route, told what did not work.
        val two = assertNotNull(l.climb(3, 3, false, 0, false, "back", "screen did NOT change", null, brain))
        assertTrue("ROUTE CHANGE" in two.note); assertEquals(2, l.tier)
        assertTrue("ROUTES TRIED THAT DID NOT WORK: back (screen did NOT change)" in asked.last(), asked.last())
        // Still repeating: the plan is rewritten, done items kept.
        val three = assertNotNull(l.climb(4, 4, false, 0, false, "back", "screen did NOT change", "1. [done] Open Zomato\n2. Find the bakery", brain))
        assertEquals(3, l.tier)
        assertEquals(listOf("Open Zomato", "Search for the bakery", "Open its page"), assertNotNull(three.plan).map { it.text })
        assertTrue(three.plan!![0].done)
        // Nothing else worked: the user, with options.
        val four = assertNotNull(l.climb(5, 5, false, 0, false, "back", "screen did NOT change", null, brain))
        assertTrue("NOTHING ELSE WORKED" in four.note && four.critical); assertEquals(4, l.tier)
        assertNull(l.climb(6, 6, false, 0, false, "back", "x", null, brain), "no further rung")
        l.progressed()
        assertEquals(0, l.tier)
    }

    @Test
    fun aDraggingPlanStepGoesStraightToANewPlan() = runTest {
        val l = RecoveryLadder()
        val rung = assertNotNull(l.climb(1, 1, true, 0, planStepDragging = true, lastDescribed = "tap \"x\"", lastOutcome = "ok", currentPlan = "1. Find it", ask = brain))
        assertEquals(3, l.tier)
        assertTrue("one step has taken ${StepBudget.PLAN_STEP_CAP} steps" in asked.single())
        assertNotNull(rung.plan)
    }

    @Test
    fun twoMissedExpectationsAreEnoughForAnotherWay() = runTest {
        val l = RecoveryLadder()
        assertTrue("TRY ANOTHER WAY" in assertNotNull(l.climb(1, 1, true, 2, false, null, "x", null, brain)).note)
    }
}
