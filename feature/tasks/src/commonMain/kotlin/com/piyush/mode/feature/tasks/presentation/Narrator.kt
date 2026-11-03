package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What Mode says while a spoken task works, like a personal assistant keeping you posted ("Found Swiggy,
 * adding your usual now."). The quick model (phone control's, lowest effort) writes each line from the task,
 * what is done and what Mode already said; [Pacing] decides when one is worth saying.
 */
public class Narrator(
    private val llm: LlmClient,
    /** The quick model and its provider; null when none is chosen. */
    private val model: () -> Pair<String, ModelProvider>?,
) {
    /** One short update, or null (nothing new worth saying, slow, or failed). */
    public suspend fun line(task: String, done: List<String>, now: String?, said: List<String>, name: String?, budgetMs: Long = BUDGET_MS): String? {
        val (id, provider) = model() ?: return null
        val user = buildString {
            append("THEIR NAME: ").append(name?.trim()?.split(' ')?.firstOrNull()?.ifBlank { null } ?: "(not known)").append('\n')
            append("THE TASK: ").append(task).append('\n')
            if (done.isNotEmpty()) append("DONE SO FAR:\n").append(done.joinToString("\n") { "- $it" }).append('\n')
            now?.takeIf { it.isNotBlank() }?.let { append("DOING NOW: ").append(it).append('\n') }
            if (said.isNotEmpty()) append("YOU ALREADY SAID:\n").append(said.joinToString("\n") { "- $it" }).append('\n')
        }
        return try {
            withTimeoutOrNull(budgetMs) {
                val reply = llm.forProvider(provider).chat(id, listOf(ChatMessage("system", SYSTEM), ChatMessage("user", user)), TOOLS)
                val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments
                (args?.get("line") as? JsonPrimitive)?.contentOrNull?.let(::clean)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * When an update is worth saying: real progress (a plan step done, a new step), not before [firstAfterMs],
     * at least [gapMs] apart, at most [max] per task.
     */
    public class Pacing(
        private val firstAfterMs: Long = FIRST_AFTER_MS,
        private val gapMs: Long = GAP_MS,
        private val max: Int = MAX_UPDATES,
    ) {
        private var lastAt: Long? = null
        private var count = 0
        private var lastProgress: String? = null

        /** Whether to say something now, [elapsedMs] into the task, with [progress] describing where it is. */
        public fun due(elapsedMs: Long, progress: String): Boolean {
            if (count >= max || elapsedMs < firstAfterMs || progress.isBlank() || progress == lastProgress) return false
            val last = lastAt
            return last == null || elapsedMs - last >= gapMs
        }

        /** An update was said (or tried) at [elapsedMs] about [progress]. */
        public fun said(elapsedMs: Long, progress: String) {
            lastAt = elapsedMs
            lastProgress = progress
            count++
        }
    }

    public companion object {
        public const val BUDGET_MS: Long = 3_000
        public const val FIRST_AFTER_MS: Long = 8_000
        public const val GAP_MS: Long = 15_000
        public const val MAX_UPDATES: Int = 4
        private const val MAX_WORDS = 18
        private const val TOOL = "say"
        /** The model's way of saying there is nothing new: stay quiet. */
        private const val NOTHING = "nothing"

        internal fun clean(text: String): String? {
            val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.filter { it.code < 0x2190 || it in "–—‘’“”…₹€£" }
                ?.replace(Regex("\\s+"), " ")
                ?.trim('"', '“', '”', '\'', '*', '_', '`', ' ')
                ?: return null
            if (line.isEmpty() || line.trim('.', '!').equals(NOTHING, ignoreCase = true)) return null
            return line.takeIf { it.split(' ').size <= MAX_WORDS }
        }

        private val SYSTEM = """
            You are Mode, the user's personal assistant, doing a task on their iPhone while they wait. Keep them
            posted out loud like a capable human assistant would: one short, natural line (at most 12 words) about
            what you just got done or are doing now. Warm and personal; their first name only now and then.
            - Say something new: never repeat what you already said, never read the task back.
            - Plain progress, not technical steps: no taps, screens, coordinates or app internals.
            - If there is nothing new worth saying, the line is exactly: nothing
            - No emoji, markdown or quotes. Reply in the language of the task.
            Call say with the line.
        """.trimIndent()

        private val TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL)
                    put("description", "What Mode says out loud now, or nothing.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("line") { put("type", "string"); put("description", "One short spoken update, or: nothing") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("line")) }
                    }
                }
            })
        }
    }
}
