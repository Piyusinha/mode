package com.piyush.mode.app.screens.common

import com.piyush.mode.feature.runs.data.RunRepository
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** One screen the agent saw during a run, and what it did there. */
public data class RunShot(val step: Int, val image: String, val action: String?)

/** The screens of a recorded run, in order. */
internal fun RunRepository.shots(runId: String): List<RunShot> {
    val events = events(runId)
    fun field(e: kotlinx.serialization.json.JsonObject, name: String) = (e[name] as? JsonPrimitive)?.contentOrNull
    val actions = events.filter { field(it, "event") == "act" }
        .associate { (it["step"] as? JsonPrimitive)?.intOrNull to field(it, "action") }
    return events.filter { field(it, "event") == "look" }.mapNotNull { e ->
        val step = (e["step"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val image = field(e, "image") ?: return@mapNotNull null
        RunShot(step, image, actions[step])
    }
}
