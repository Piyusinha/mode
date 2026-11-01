package com.piyush.mode.app.screens.common

import com.piyush.mode.feature.runs.data.RunRepository
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** One screen the agent saw during a run, and what it was doing there in plain words ("Adding it to Dinner"). */
public data class RunShot(val step: Int, val image: String, val label: String?)

/** The screens of a recorded run, in order. */
internal fun RunRepository.shots(runId: String): List<RunShot> {
    val events = events(runId)
    fun field(e: kotlinx.serialization.json.JsonObject, name: String) = (e[name] as? JsonPrimitive)?.contentOrNull
    // The agent's own words for each step (its reason), never the raw action ("tap (411, 273)").
    val reasons = events.filter { field(it, "event") == "answer" }
        .associate { e ->
            (e["step"] as? JsonPrimitive)?.intOrNull to (e["arguments"] as? kotlinx.serialization.json.JsonObject)
                ?.let { field(it, "reason") }?.takeIf { it.isNotBlank() }
        }
    return events.filter { field(it, "event") == "look" }.mapNotNull { e ->
        val step = (e["step"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val image = field(e, "image") ?: return@mapNotNull null
        RunShot(step, image, reasons[step])
    }
}
