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
        /** Several steps without progress: the brain should decide what to do (cheaper than a strong step). */
        data object Decide : Verdict
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
    /**
     * Steps in a row that moved nothing forward: a plan, a repeated lookup, a refused or held action,
     * a wait, a tap that left the same page. Seen on the phone: 16 steps alternating "plan saved / not
     * saved", six taps on a focused field: separate counters never added up to a stop.
     */
    private var stalls = 0
    private var decideAsked = false
    /** Scrolls in a row that went the other way from the one before (down, up, down…). */
    private var lastScroll: String? = null
    private var reversals = 0
    /** Scrolls in a row the same way, for the Sort/Filter hint. */
    private var sameWay = 0
    /** A reversal was already held back in this run of scrolls. */
    private var heldReversal = false
    /** Screens seen right after phone actions, newest last. */
    private val recent = ArrayDeque<String>()
    /** Phone actions (kind and target), newest last, for the same action done again and again. */
    private val actions = ArrayDeque<String>()
    /** How many times the last looked-at screen was seen in the window. */
    var visits: Int = 0
        private set

    /**
     * Each phone action: how many times this same action (kind and target) was taken in the last
     * [ACTION_WINDOW], this one included. Seen on the phone: back, escape, back… eleven times between two
     * screens; each stop looked a little different, so the same-screen count never saw it.
     */
    /** The last action moved the screen on: it was progress, not a repeat (reading a long page by scrolling). */
    fun forgetLastAction() {
        if (actions.isNotEmpty()) actions.removeLast()
    }

    fun repeated(signature: String): Int {
        actions.addLast(signature)
        if (actions.size > ACTION_WINDOW) actions.removeFirst()
        return actions.count { it == signature }
    }

    /** Each look at the screen; [changed] is null when the step before was not a phone action. */
    fun looked(signature: String, changed: Boolean?): Verdict {
        visits = 0
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
        if (stalls >= STOP_STALLS) return Verdict.Stop("it made no progress in $stalls steps")
        if (stalls >= DECIDE_STALLS && !decideAsked) {
            decideAsked = true
            return Verdict.Decide
        }
        if (critical) {
            critical = false
            return stepUp(null)
        }
        // Seen on the phone: ten scrolls down and up over one diary page, the answer already in the notes.
        // Each stop is a little different, so the same-screen count never saw it.
        if (reversals >= MAX_REVERSALS) {
            reversals = 0
            return stepUp(
                "you have scrolled back and forth over the same page. To find something further down, keep scrolling " +
                    "the same way until it shows or the page ends; if your notes already answer the task, call done",
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
        sameWay = if (direction != null && direction == lastScroll) sameWay + 1 else if (direction != null) 1 else 0
        if (direction == null) heldReversal = false
        lastScroll = direction
    }

    /**
     * Seen on the phone: up, down, up, down over one list, again and again, even with the strong
     * model stepping in. After two reversals, every further scroll back the other way is held
     * until the agent does something else (opens an item, taps, finishes).
     */
    fun holdReversal(direction: String?): String? {
        val last = lastScroll
        if (direction == null || last == null || direction == last || reversals < 2) return null
        heldReversal = true
        return last
    }

    /** Scrolls in a row the same way so far (the hint to use the app's Sort or Filter comes at 3). */
    fun scrollsSameWay(): Int = sameWay

    /** The step moved things forward: a new page, a confirmed change. */
    fun progressed() {
        stalls = 0
        decideAsked = false
    }

    /** The step moved nothing forward. */
    fun stalled() { stalls++ }

    /** A reply with no usable action. */
    fun badReply() { badReplies++; stalls++ }

    /** A phone action went through. */
    fun acted() { badReplies = 0; waits = 0; plans = 0 }

    /** The model planned or looked something up instead of acting; returns how many times in a row. */
    fun thought(): Int { stalls++; return ++plans }

    /** The model chose to wait; returns how many waits in a row. */
    fun waited(): Int { stalls++; return ++waits }

    private fun stepUp(note: String?): Verdict {
        // Without a strong model the step-up is declined: the counters keep counting towards a stop.
        if (!canStepUp) return if (note != null) Verdict.StepUp(note) else Verdict.Fine
        sinceStepUp = 0
        badReplies = 0
        waits = 0
        plans = 0
        steppedUp = true
        return Verdict.StepUp(note)
    }

    companion object {
        const val WINDOW = 12
        const val STEP_UP_UNCHANGED = 2
        const val STOP_UNCHANGED = 4
        const val WARN_VISITS = 4
        const val STOP_VISITS = 6
        /** Steps without progress before the brain decides, and before the run stops. */
        const val DECIDE_STALLS = 3
        /** Room for the recovery ladder (another way, another route, a new plan, a question) before a stop. */
        const val STOP_STALLS = 10
        const val ACTION_WINDOW = 8
        /** The same action this many times in the window: the route is not working. */
        const val REPEAT_ROUTE = 3
        /** Changes of scroll direction in a row before the strong model takes over. */
        const val MAX_REVERSALS = 3
    }
}
