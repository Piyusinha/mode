package com.piyush.mode.feature.memory.data

import com.piyush.mode.feature.memory.domain.MemoryKind
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
 * What Mode says out loud when handed a task, written by the AI like a personal assistant would say it:
 * short, warm, in the user's words, naming what it is about to do, and using what Mode knows about them
 * ("On it, Piyush. Your usual oat latte from Starbucks, coming up."). Never a fixed line.
 *
 * It must not hold the task up: [line] gives up after [budgetMs] and returns null, and the caller then
 * says its own short line.
 */
public class Acknowledger(
    private val llm: LlmClient,
    private val memory: MemoryRepository?,
    /** The fast model (phone control's) and its provider: the line has to come back in about a second. */
    private val model: () -> Pair<String, ModelProvider>?,
) {
    /**
     * The spoken line for [task] said by [name] at [hour] (0–23), or null when the AI is slow, fails or
     * isn't set up. [private] tasks use no memory.
     */
    public suspend fun line(task: String, name: String?, hour: Int, private: Boolean = false, budgetMs: Long = BUDGET_MS): String? {
        val said = task.trim()
        if (said.isEmpty()) return null
        val (id, provider) = model() ?: return null
        return try {
            withTimeoutOrNull(budgetMs) {
                val user = buildString {
                    append("THEIR NAME: ").append(name?.trim()?.split(' ')?.firstOrNull()?.ifBlank { null } ?: "(not known)").append('\n')
                    append("TIME OF DAY: ").append(partOfDay(hour)).append('\n')
                    if (!private) {
                        val known = memory?.ofKind(MemoryKind.Fact).orEmpty().take(FACTS).map { it.text }
                        if (known.isNotEmpty()) append("WHAT YOU KNOW ABOUT THEM:\n").append(known.joinToString("\n") { "- $it" }).append('\n')
                    }
                    append("WHAT THEY JUST ASKED: ").append(said)
                }
                val reply = llm.forProvider(provider).chat(id, listOf(ChatMessage("system", SYSTEM), ChatMessage("user", user)), TOOLS)
                val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments
                ((args?.get("line") as? JsonPrimitive)?.contentOrNull ?: reply.text).let(::clean)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    public companion object {
        /** How long a task may wait for its spoken line. */
        public const val BUDGET_MS: Long = 1_200
        private const val FACTS = 8
        private const val MAX_WORDS = 20
        private const val TOOL = "say"

        internal fun partOfDay(hour: Int): String = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "late night"
        }

        /** One spoken line: no quotes, markdown or emoji, not too long; null if nothing usable came back. */
        internal fun clean(text: String?): String? {
            val line = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
                ?.filter { it.code < 0x2190 || it in "–—‘’“”…₹€£" }
                ?.replace(Regex("\\s+"), " ")
                ?.trim('"', '“', '”', '\'', '*', '_', '`', ' ')
                ?: return null
            if (line.isEmpty()) return null
            return if (line.split(' ').size <= MAX_WORDS) line else null
        }

        private val SYSTEM = """
            You are Mode, the user's personal assistant on their iPhone, speaking out loud. They have just asked you
            to do something; you are about to start. Say one short, natural line (at most 14 words), the way a
            warm, capable human assistant would: acknowledge it and say what you are about to do, in your own words.
            - Sound personal: use their first name now and then (not every time), and use what you know about them
              when it fits ("your usual", "Asha on WhatsApp").
            - For a question, say you'll check or look it up; don't answer it yet.
            - Don't read their request back word for word, don't promise a result, don't ask anything.
            - Plain spoken words: no emoji, no markdown, no quotes. Speak English. Use another language only when their whole request is in it; a few Hindi or other words mixed into English still mean English.
            Call say with the line.
        """.trimIndent()

        private val TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL)
                    put("description", "What Mode says out loud before starting.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("line") { put("type", "string"); put("description", "One short spoken line.") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("line")) }
                    }
                }
            })
        }
    }
}
