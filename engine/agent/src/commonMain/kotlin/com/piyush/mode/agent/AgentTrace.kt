package com.piyush.mode.agent

import com.piyush.mode.llm.Usage
import kotlinx.serialization.json.JsonObject

/**
 * Everything the agent does, in detail, for debugging and for comparing
 * models: what it saw, what it asked, what the model answered, what it did,
 * and how long each part took. All callbacks run on the agent's coroutine.
 */
interface AgentTrace {
    fun runStarted(task: String, config: AgentConfig, tools: List<String>) {}

    /** The screen as the model will see it, and how long looking took. */
    fun looked(step: Int, jpeg: ByteArray, imageWidth: Int, imageHeight: Int, texts: List<Triple<String, Int, Int>>, millis: Long) {}

    /** What is about to be sent: the user message (the system prompt is fixed). */
    fun asking(step: Int, model: String, steppedUp: Boolean, prompt: String) {}

    /** The model's raw choice, its cost, and how long it took; [error] if the call failed. */
    fun answered(step: Int, model: String, toolName: String?, arguments: JsonObject?, text: String, usage: Usage?, millis: Long, error: String? = null) {}

    /** What was done on the phone and how it went. */
    fun acted(step: Int, action: String, op: String?, outcome: String, millis: Long) {}

    /** A fact the model kept for the rest of the task. */
    fun noted(step: Int, text: String) {}

    /** A `done` that was sent back once to be checked against the screen. */
    fun verifyRejected(step: Int, why: String) {}

    /** The run was stopped because it was getting nowhere. */
    fun stuck(step: Int, why: String) {}

    fun runEnded(result: AgentResult) {}

    object None : AgentTrace
}
