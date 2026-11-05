package com.piyush.mode.agent

/**
 * Whether a give-up is accepted. Seen on the phone: "CLOSED RIGHT NOW · Schedule" and a give-up with twenty
 * steps left; a flight given up after one search. A give-up needs evidence from the screen, a blocker that is
 * not the user's to decide (or a question already asked), and more than one route tried while steps remain.
 * Research: give up only with evidence; repetition alone does not establish impossibility (VLAA-GUI); asking
 * the user is the alternative to giving up (MobileWorld).
 */
internal object GiveUpGate {
    /** Blockers that are the user's call: ask with options first. */
    private val USERS_CALL = setOf("closed", "unavailable", "needs_choice", "needs_info")
    private const val HANDOFF = "login_or_payment"
    private const val MIN_ROUTES = 2
    private const val MIN_WORD = 4
    private val STOP = setOf("there", "which", "where", "this", "that", "with", "from", "have", "been", "were", "they", "them", "then", "than", "when", "what", "could", "would", "cannot", "could")

    /**
     * Null when the give-up stands; else why it is refused and what to do instead. [routes]: routes the agent
     * says it tried plus the ones the harness saw fail; [asked]: the user was asked something this task.
     */
    fun refuse(
        blockedBy: String?, routes: Int, evidence: String, asked: Boolean, stepsLeft: Boolean,
        screen: List<String>, notes: List<String>,
    ): String? {
        val blocker = blockedBy?.trim()?.lowercase().orEmpty()
        if (blocker == HANDOFF) {
            return "not accepted: this needs the user, not a give-up. Call wait_for_user with what only they can do " +
                "(the PIN, the password, Face ID), then carry on from the screen"
        }
        if (blocker in USERS_CALL && !asked) {
            return "not accepted: this is the user's call. ask_user with 2–3 concrete options from what you found " +
                "(the later slot, the place that is open, the close alternative, where you looked), then do what they pick"
        }
        if (stepsLeft && routes < MIN_ROUTES) {
            return "not accepted: one route is not enough to give up with steps left. Try another: another app; Mail, " +
                "Messages, Wallet, Calendar or the app's own orders or bookings; a search with different words; scheduling " +
                "it; or ask_user with options. Say in routes_tried what you tried and what happened"
        }
        if (evidence.isNotBlank() && !quotes(evidence, screen + notes)) {
            return "not accepted: the evidence must quote what the screen shows or what you noted; it says \"${evidence.take(80)}\" " +
                "and nothing like it is on the screen. Look again and quote it, or keep going"
        }
        return null
    }

    /** At least one content word of [text] appears in [seen]. */
    internal fun quotes(text: String, seen: List<String>): Boolean {
        val words = norm(text).split(' ').filter { it.length >= MIN_WORD && it !in STOP }
        if (words.isEmpty()) return true
        val haystack = seen.map(::norm)
        return words.any { w -> haystack.any { it.contains(w) } }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }
}
