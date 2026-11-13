package com.piyush.mode.app.screens.debug

import com.piyush.mode.core.telemetry.SpanRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** What a finding is about, so the screen can show its icon and Mode its repair. */
public enum class FindingKind { NoAction, AiFailed, Stuck, OutOfSteps, OutOfTime, BackAndForth, GaveUp, Unconfirmed, PhoneUnreachable, WaitedForYou, Interrupted, NotStarted, PhoneOk, AiOk }

/** [problem]: something went wrong (orange); otherwise it explains or confirms (no colour, or a check). */
public data class Finding(val kind: FindingKind, val title: String, val detail: String, val problem: Boolean, val ok: Boolean = false)

/**
 * What went wrong in a task, from its step log and its trace, by rules (no AI): the same reading a person does
 * of the run (six "no valid action" steps, a stop as stuck, a repeated back, an order it couldn't confirm).
 */
public object TaskDiagnosis {
    /** [outcome]: the run's (done, gave_up, stopped, failed, not_started, running); [summary]: its reason or answer. */
    public fun of(outcome: String?, summary: String?, steps: List<JsonObject>, spans: List<SpanRecord>, device: String = "this iPhone", os: String = "iOS"): List<Finding> {
        val out = mutableListOf<Finding>()
        val acts = steps.filter { it.str("event") == "act" }
        val said = summary.orEmpty()

        val noAction = acts.count { it.str("action") == "(no valid action)" }
        if (noAction >= NO_ACTION_MIN) {
            out += Finding(FindingKind.NoAction, "Your AI answered without choosing an action",
                "$noAction times, so nothing on screen changed. Health & repair can test it.", problem = true)
        }
        val modelErrors = spans.filter { it.name == "gen_ai.chat" && it.error != null }
        if (modelErrors.isNotEmpty() || outcome == "failed") {
            val why = modelErrors.lastOrNull()?.error ?: said.take(MAX_DETAIL)
            out += Finding(FindingKind.AiFailed, "Your AI couldn't answer", why.ifBlank { "It returned an error" }, problem = true)
        }
        if (spans.any { it.name == "mode.phone.connect" && it.error != null } || "could not connect" in said.lowercase()) {
            out += Finding(FindingKind.PhoneUnreachable, "Mode couldn't reach $device", "Phone control didn't connect", problem = true)
        }
        when {
            outcome == "running" ->
                out += Finding(FindingKind.Interrupted, "Mode was closed while it worked", "$os ended Mode before the task finished; Try again picks it up", problem = true)
            outcome == "not_started" ->
                out += Finding(FindingKind.NotStarted, "It didn't start", said.take(MAX_DETAIL), problem = true)
            outcome == "stopped" && said.startsWith("Stuck") ->
                out += Finding(FindingKind.Stuck, "Mode got stuck", said.substringAfter("Stuck:").substringBefore(". Last").trim().replaceFirstChar { it.uppercase() }.ifBlank { "The screen stopped changing" }, problem = true)
            outcome == "stopped" && "limit of" in said && "steps" in said ->
                out += Finding(FindingKind.OutOfSteps, "It ran out of steps", "It was still working when the step limit came", problem = true)
            outcome == "stopped" && "longer than" in said && "minutes" in said ->
                out += Finding(FindingKind.OutOfTime, "It took too long", "Tasks stop after 15 minutes", problem = true)
            outcome == "stopped" && "Waited for you" in said ->
                out += Finding(FindingKind.WaitedForYou, "It waited for you", "Something only you can do (a PIN, Face ID) wasn't done in time", problem = false)
        }
        if (Regex("couldn.t (verify|confirm)", RegexOption.IGNORE_CASE).containsMatchIn(said)) {
            out += Finding(FindingKind.Unconfirmed, "It couldn't confirm it went through",
                "Check the app's orders or bookings before trying again, so nothing happens twice", problem = true)
        }
        val repeats = acts.groupingBy { (it.str("action") ?: "") + "|" + (it.str("reason") ?: "") }.eachCount()
            .filterKeys { !it.startsWith("(no valid action)") }.maxByOrNull { it.value }
        if (repeats != null && repeats.value >= REPEAT_MIN) {
            out += Finding(FindingKind.BackAndForth, "It went back and forth",
                "The same step ${repeats.value} times: “${repeats.key.substringBefore('|').take(MAX_DETAIL)}”", problem = false)
        }
        if (outcome == "gave_up" && out.none { it.problem }) {
            out += Finding(FindingKind.GaveUp, "Mode gave up", said.substringBefore(". ").take(MAX_DETAIL), problem = false)
        }
        return out
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private const val NO_ACTION_MIN = 3
    private const val REPEAT_MIN = 4
    private const val MAX_DETAIL = 140
}
