package com.piyush.mode.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepBudgetTest {
    @Test
    fun progressExtendsTheLimitUpToTheCeiling() {
        val b = StepBudget(30, 60)
        assertEquals(30, b.limit)
        b.progressed(); assertEquals(40, b.limit)
        repeat(5) { b.progressed() }
        assertEquals(60, b.limit, "never beyond the ceiling")
        b.refund(); assertEquals(60, b.limit)
    }

    @Test
    fun theWarningComesOncePerLimitNearItsEnd() {
        val b = StepBudget(10, 20)
        assertNull(b.warning(6))
        assertTrue("3 steps left" in assertNotNull(b.warning(7)))
        assertNull(b.warning(8), "once per limit")
        b.progressed()
        assertNull(b.warning(13))
        assertTrue("6 steps left" in assertNotNull(b.warning(14)), "again for the new limit")
    }

    @Test
    fun aSubmitInViewAtTheLimitEarnsGraceOnce() {
        val b = StepBudget(5, 5)
        assertFalse(b.grace(6, submitVisible = false))
        assertTrue(b.grace(6, submitVisible = true))
        assertEquals(8, b.limit)
        assertFalse(b.grace(9, submitVisible = true), "only once")
    }

    @Test
    fun aPlanStepThatDragsOnAsksForANewPlan() {
        val b = StepBudget(30, 60)
        assertFalse(b.planStep(done = 1), "step 1 reached")
        repeat(StepBudget.PLAN_STEP_CAP - 1) { assertFalse(b.planStep(done = 1)) }
        assertTrue(b.planStep(done = 1), "twelve steps on one plan step")
        assertFalse(b.planStep(done = 2), "the next step was reached: counted afresh")
    }
}
