package com.piyush.mode.feature.runs.domain

import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import kotlin.math.roundToInt

/** One recorded run, as listed in history. */
public data class RunSummary(
    val id: String,
    val task: String,
    /** running, done, gave_up, stopped, failed or not_started. */
    val outcome: String,
    val summary: String,
    val model: String?,
    val steps: Int?,
    val millis: Long?,
    val costUsd: Double?,
    /** Set for runs that belong to a model comparison. */
    val tag: String?,
)

/** A one-line description of how a run went, with time, steps and cost. */
public fun describeResult(result: AgentResult): String {
    val cost = "$" + ((result.costUsd * 10000).roundToInt() / 10000.0)
    val time = "${result.millis / 1000}s"
    return when (val o = result.outcome) {
        is AgentOutcome.Done -> (if (o.success) "Done: " else "Could not finish: ") + o.summary + " ($time, ${result.steps} steps, $cost)"
        is AgentOutcome.Stopped -> "Stopped: ${o.why} ($time, ${result.steps} steps, $cost)"
        is AgentOutcome.Failed -> "Failed: ${o.why}"
    }
}
