package com.piyush.mode.feature.memory.data

import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.memory.domain.SaveResult
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What the user teaches Mode in a chat ("Next time use Control Center for this") or on a step it got wrong: turned
 * into one clear tip for the app it is about, saved as theirs (Memory shows "You taught this"), and used on the next
 * task in that app like any tip. The model writes the tip from the user's words and the task; without it, their
 * words are kept as they are.
 */
public class Teacher(
    private val llm: LlmClient,
    private val memory: MemoryRepository,
    /** The model that writes the tip, and its provider: the brain's. */
    private val model: () -> Pair<String, ModelProvider>,
) {
    /** A saved lesson: the tip (for Edit and Undo) and what Mode says back. */
    public class Lesson(public val item: MemoryItem, public val reply: String)

    /**
     * Saves [said] as a tip. [task] is what the user asked; [step] the step it is about, when they marked one.
     * Null when it could not be saved (empty, or it looked like a secret).
     */
    public suspend fun teach(said: String, task: String, step: String? = null): Lesson? {
        val words = said.trim()
        if (words.isEmpty()) return null
        val written = try { write(words, task, step) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        val app = written?.app?.takeIf { it.isNotBlank() }
        val tip = written?.tip?.takeIf { it.isNotBlank() } ?: words
        val title = written?.title?.takeIf { it.isNotBlank() } ?: task.take(TITLE_MAX).ifBlank { words.take(TITLE_MAX) }
        val draft = MemoryDraft(
            kind = MemoryKind.Tip, subject = (app ?: title).lowercase(), title = title, text = tip,
            evidence = TAUGHT + (step?.let { " ($it)" } ?: " in chat") + ": \"$words\"",
            keywords = Words.of("$title $tip").take(KEYWORDS).toList(), app = app,
        )
        val item = when (val saved = memory.save(draft)) {
            is SaveResult.Added -> saved.item
            is SaveResult.Updated -> saved.item
            is SaveResult.Unchanged -> saved.item
            is SaveResult.Rejected -> return null
        }
        return Lesson(item, written?.reply?.takeIf { it.isNotBlank() } ?: "Got it. I'll do it that way next time.")
    }

    private class Written(val app: String?, val title: String?, val tip: String?, val reply: String?)

    private suspend fun write(said: String, task: String, step: String?): Written? {
        val (name, provider) = model()
        val user = buildString {
            append("THE TASK: ").append(task.ifBlank { "(none)" }).append('\n')
            step?.let { append("THE STEP IT IS ABOUT: ").append(it).append('\n') }
            append("WHAT THE USER SAID: ").append(said)
        }
        val reply = llm.forProvider(provider).chat(name, listOf(ChatMessage("system", SYSTEM), ChatMessage("user", user)), TOOLS)
        val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments ?: return null
        fun str(k: String) = (args[k] as? JsonPrimitive)?.contentOrNull?.trim()
        return Written(str("app"), str("title"), str("tip"), str("reply"))
    }

    public companion object {
        /** Evidence of a tip the user taught (Memory shows it as theirs). */
        public const val TAUGHT: String = "Taught by you"
        private const val TOOL = "save_lesson"
        private const val TITLE_MAX = 60
        private const val KEYWORDS = 12

        /** True for an item the user taught. */
        public fun taught(item: MemoryItem): Boolean = item.evidence.startsWith(TAUGHT)

        /**
         * Whether a chat message teaches rather than asks for a task: "next time …", "from now on …", "always …",
         * "never …", "remember to …", "you should have …", "instead …".
         */
        public fun isLesson(text: String): Boolean = LESSON.containsMatchIn(text.trim())

        private val LESSON = Regex(
            """\b(next time|from now on|in (the )?future|every time|always|never|remember (that|to|this)|you should (have|'ve)?|""" +
                """should've|instead of|don't (go|use|open|scroll|tap)|do not (go|use|open|scroll|tap))\b""",
            RegexOption.IGNORE_CASE,
        )

        private val SYSTEM = """
            The user is teaching Mode, a personal assistant that operates their iPhone, how to do something next time.
            Turn what they said into one short tip Mode will follow on the next task in that app. Name the app the tip
            is about (Settings, Swiggy, WhatsApp…), give it a short title naming what it is for ("Dark Mode"), and
            write the tip as an instruction with the exact on-screen names to tap or search for. Keep the user's
            meaning; add nothing they did not say. Then write what Mode says back in one short, warm sentence that
            names what it will do ("Got it. Next time I'll switch Dark Mode from Control Center."). Call save_lesson.
        """.trimIndent()

        private val TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL)
                    put("description", "The tip to keep for next time, and Mode's reply.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            listOf(
                                "app" to "The app the tip is about.",
                                "title" to "What it is for, in a few words (\"Dark Mode\").",
                                "tip" to "The instruction for next time, one or two sentences.",
                                "reply" to "What Mode says back, one short sentence.",
                            ).forEach { (k, about) -> putJsonObject(k) { put("type", "string"); put("description", about) } }
                        }
                        putJsonArray("required") { listOf("app", "title", "tip", "reply").forEach { add(JsonPrimitive(it)) } }
                    }
                }
            })
        }
    }
}
