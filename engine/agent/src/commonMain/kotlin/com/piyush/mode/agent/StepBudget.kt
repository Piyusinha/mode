package com.piyush.mode.agent

import kotlin.math.min

/**
 * Steps a task may take: a base, extended while the task makes real progress (a new page, a confirmed change,
 * a plan step done), up to a ceiling. Seen on the phone: a flight check stopped at step 30 of 30 one tap from
 * "Search Flight". Also a warning as the budget nears its end, a few grace steps when the last action typed
 * and the submit is in view, and a cap on how long one plan step may take before the plan is rewritten.
 */
internal class StepBudget(base: Int, private val ceiling: Int, private val extendBy: Int = EXTEND) {
    var limit: Int = min(base, ceiling)
        private set
    private var warnedAt = -1
    private var graced = false
    private var planDone = 0
    private var sincePlanStep = 0

    /** Real progress: more steps, up to the ceiling. */
    fun progressed() {
        if (limit < ceiling) limit = min(ceiling, limit + extendBy)
    }

    /** A step the phone, not the agent, wasted (phone control stalled): given back, within the ceiling. */
    fun refund() {
        if (limit < ceiling) limit++
    }

    /** Once per limit, from [WARN_SHARE] of it: how many steps are left. */
    fun warning(step: Int): String? {
        if (step < limit * WARN_SHARE || warnedAt == limit) return null
        warnedAt = limit
        return "BUDGET: ${limit - step} steps left. Converge: finish the task, or ask the user with options"
    }

    /** At the limit with a submit in view right after typing: a few more steps, once. */
    fun grace(step: Int, submitVisible: Boolean): Boolean {
        if (graced || step <= limit || !submitVisible) return false
        graced = true
        limit += GRACE
        return true
    }

    /**
     * Each step, with how many plan items are done: true when the current plan step has taken [PLAN_STEP_CAP]
     * steps without the next one being reached (time to rewrite the plan), counted afresh after that.
     */
    fun planStep(done: Int): Boolean {
        if (done > planDone) {
            planDone = done
            sincePlanStep = 0
            return false
        }
        sincePlanStep++
        if (sincePlanStep < PLAN_STEP_CAP) return false
        sincePlanStep = 0
        return true
    }

    companion object {
        const val EXTEND = 10
        const val GRACE = 3
        const val WARN_SHARE = 0.7
        const val PLAN_STEP_CAP = 12
    }
}
