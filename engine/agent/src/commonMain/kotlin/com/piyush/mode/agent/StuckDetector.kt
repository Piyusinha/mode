package com.piyush.mode.agent

/**
 * Notices when a run is getting nowhere: actions that leave the screen as it
 * was, or a run going round in circles between the same screens. It first
 * steps up to the stronger model; if that doesn't help, it stops the run with
 * an honest reason, instead of burning the rest of the step and cost budget.
 */
class StuckDetector(private val canStepUp: Boolean) {
    sealed interface Verdict {
        data object Fine : Verdict
        /** Ask the stronger model this step; [note] is added to the step history when set. */
        class StepUp(val note: String? = null) : Verdict
        class Stop(val why: String) : Verdict
    }

    /** Phone actions in a row that left the screen as it was. */
    private var unchanged = 0
    /** The same, counted again from zero after each step-up, so the main model gets two more tries. */
    private var sinceStepUp = 0
    private var badReplies = 0
    private var waits = 0
    /** plan calls in a row with no phone action between them. */
    private var plans = 0
    private var steppedUp = false
    /** Screens seen right after phone actions, newest last. */
    private val recent = ArrayDeque<String>()

    /** Each look at the screen; [changed] is null when the step before was not a phone action. */
    fun looked(signature: String, changed: Boolean?): Verdict {
        var visits = 0
        if (changed != null) {
            recent.addLast(signature)
            if (recent.size > WINDOW) recent.removeFirst()
            visits = recent.count { it == signature }
            if (changed) {
                unchanged = 0
                sinceStepUp = 0
            } else {
                unchanged++
                sinceStepUp++
            }
        }
        if (unchanged >= STOP_UNCHANGED && (steppedUp || !canStepUp)) {
            return Verdict.Stop("the screen stopped changing after $unchanged tries")
        }
        if (visits >= STOP_VISITS) return Verdict.Stop("it kept coming back to the same screen ($visits times in $WINDOW steps)")
        return when {
            visits >= WARN_VISITS -> stepUp(
                "you have been on this screen $visits times in the last $WINDOW steps: this route is not working, try a different one",
            )
            plans >= 3 -> stepUp(
                "you have re-planned $plans times in a row without acting: stop planning and do the next item with a phone action",
            )
            sinceStepUp >= STEP_UP_UNCHANGED || badReplies >= 2 || waits >= 3 -> stepUp(null)
            else -> Verdict.Fine
        }
    }

    /** A reply with no usable action. */
    fun badReply() { badReplies++ }

    /** A phone action went through. */
    fun acted() { badReplies = 0; waits = 0; plans = 0 }

    /** The model rewrote its plan; returns how many times in a row. */
    fun planned(): Int = ++plans

    /** The model chose to wait; returns how many waits in a row. */
    fun waited(): Int = ++waits

    private fun stepUp(note: String?): Verdict {
        sinceStepUp = 0
        badReplies = 0
        waits = 0
        plans = 0
        if (!canStepUp) return if (note != null) Verdict.StepUp(note) else Verdict.Fine
        steppedUp = true
        return Verdict.StepUp(note)
    }

    companion object {
        const val WINDOW = 12
        const val STEP_UP_UNCHANGED = 2
        const val STOP_UNCHANGED = 4
        const val WARN_VISITS = 4
        const val STOP_VISITS = 6
    }
}
