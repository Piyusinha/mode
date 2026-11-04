package com.piyush.mode.agent

/**
 * What Mode does when a stretch of steps gets nowhere, one rung at a time: another way to do the same step
 * (no model call), another route from the planning mind (steering clear of the routes already tried), a new
 * plan, then the user with options. Progress puts the agent back on the ground. Seen on the phone: eleven
 * back taps between two screens, then a give-up with twenty steps left, and nothing else ever tried.
 */
internal class RecoveryLadder {
    /** The rung this stretch without progress has reached (0: not on the ladder). */
    var tier: Int = 0
        private set
    /** Routes that did not work, for the planning mind to steer clear of. */
    val routes = mutableListOf<String>()

    class Step(val note: String, val plan: List<PlanItem>? = null, val critical: Boolean = false)

    fun progressed() { tier = 0 }

    fun routesTried(): String = if (routes.isEmpty()) "" else " ROUTES TRIED THAT DID NOT WORK: " + routes.joinToString("; ") + "."

    /**
     * Each look: the next rung, if one is due. [repeats]: how many times the last action was taken in the
     * window; [visits]: how often this screen was seen; [changed]: whether the last action changed anything;
     * [expectMisses]: actions in a row that did not do what the model expected; [planStepDragging]: one plan
     * step has taken too many steps; [ask] puts a question to the planning mind.
     */
    suspend fun climb(
        repeats: Int, visits: Int, changed: Boolean?, expectMisses: Int, planStepDragging: Boolean,
        lastDescribed: String?, lastOutcome: String, currentPlan: String?, ask: suspend (String) -> String,
    ): Step? {
        val repeating = repeats >= StuckDetector.REPEAT_ROUTE || visits >= StuckDetector.WARN_VISITS
        return when {
            tier < 1 && (repeats >= 2 && changed == false || expectMisses >= 2) -> {
                tier = 1
                Step(TRY_ANOTHER_WAY)
            }
            tier < 2 && repeating -> {
                tier = 2
                lastDescribed?.let { routes += "$it (${lastOutcome.take(80)})" }
                Step(" (ROUTE CHANGE: " + ask(
                    "The assistant keeps doing the same thing without getting anywhere.${routesTried()} Name a different route to " +
                        "the goal (another screen, search, tab, filter or app), not one of those; or the question to ask the user with 2–3 options.",
                ) + ")")
            }
            tier < 3 && (tier == 2 && repeating || planStepDragging) -> {
                tier = 3
                val why = if (planStepDragging) "one step has taken ${StepBudget.PLAN_STEP_CAP} steps" else "two routes failed"
                val answer = ask(
                    "The current plan is not working ($why).${routesTried()} Rewrite the remaining plan as numbered steps, " +
                        "a different approach, each step a result to reach.",
                )
                val items = PlanItem.parse(answer.substringAfter("decided: ").substringBefore(". Do that now")).filter { it.text.isNotBlank() }
                if (items.isEmpty()) Step(" (REPLAN: $answer)")
                else {
                    val done = currentPlan?.let { PlanItem.parse(it).filter { item -> item.done } }.orEmpty()
                    Step(" (REPLANNED by your planning mind: follow the plan shown)", done + items.map { PlanItem(it.text, false) })
                }
            }
            tier == 3 && repeating -> {
                tier = 4
                Step(NOTHING_ELSE_WORKED, critical = true)
            }
            else -> null
        }
    }

    companion object {
        const val TRY_ANOTHER_WAY = " (TRY ANOTHER WAY for this step: search instead of browsing, scroll the other way, dismiss the keyboard " +
            "or dialog first, tap the visible < instead of swiping back, or type with the field's x and y)"
        const val NOTHING_ELSE_WORKED = " (NOTHING ELSE WORKED: ask_user now with 2–3 concrete options of what you found, " +
            "or wait_for_user if only they can do it; give up only if nothing can work)"

        /** The plan as the model sees it: numbered, done items marked. */
        fun render(items: List<PlanItem>): String =
            items.mapIndexed { i, item -> "${i + 1}. ${if (item.done) "[done] " else ""}${item.text}" }.joinToString("\n")
    }
}
