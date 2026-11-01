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
    /** plan and recall calls in a row with no phone action between them. */
    private var plans = 0
    private var steppedUp = false
    /** A moment that matters (a change held back, plan churn): the next decision goes to the strong model. */
    private var critical = false
    /** Scrolls in a row that went the other way from the one before (down, up, down…). */
    private var lastScroll: String? = null
    private var reversals = 0
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
        if (critical) {
            critical = false
            return stepUp(null)
        }
        // Seen on the phone: ten scrolls down and up over one diary page, the answer already in the notes.
        // Each stop is a little different, so the same-screen count never saw it.
        if (reversals >= MAX_REVERSALS) {
            reversals = 0
            return stepUp(
                "you have scrolled back and forth over the same page; your notes already hold what you read there. " +
                    "If they answer the task, call done now; otherwise read the page once, top to bottom",
            )
        }
        return when {
            visits >= WARN_VISITS -> stepUp(
                "you have been on this screen $visits times in the last $WINDOW steps: this route is not working, try a different one",
            )
            plans >= 3 -> stepUp(
                "you have planned or looked things up $plans times in a row without acting: act now with a phone action",
            )
            sinceStepUp >= STEP_UP_UNCHANGED || badReplies >= 2 || waits >= 3 -> stepUp(null)
            else -> Verdict.Fine
        }
    }

    /** The next decision matters more than usual: ask the strong model (when there is one). */
    fun critical() { critical = true }

    /** Each phone action: the scroll's direction, or null for anything else (which ends a back-and-forth). */
    fun scrolled(direction: String?) {
        reversals = when {
            direction == null -> 0
            lastScroll != null && direction != lastScroll -> reversals + 1
            else -> reversals
        }
        lastScroll = direction
    }

    /** A reply with no usable action. */
    fun badReply() { badReplies++ }

    /** A phone action went through. */
    fun acted() { badReplies = 0; waits = 0; plans = 0 }

    /** The model planned or looked something up instead of acting; returns how many times in a row. */
    fun thought(): Int = ++plans

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
        /** Changes of scroll direction in a row before the strong model takes over. */
        const val MAX_REVERSALS = 3
    }
}
