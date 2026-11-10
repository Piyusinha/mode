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

/** What is coming up in the user's day, for Mode's first line: read from the iPhone, never invented. */
public data class DayContext(
    /** "Thursday 6 November, 18:20". */
    val now: String,
    /** Today's calendar from now, one line each ("Thu 6 Nov 19:00–21:00 · Dinner with Priya · Toit"). */
    val events: List<String> = emptyList(),
    /** Repeats due in the next few hours ("Read today's calendar at 20:00"). */
    val repeats: List<String> = emptyList(),
    /** The last task today and how it went ("Turn on Do Not Disturb · done"), for a follow-up. */
    val lastTask: String? = null,
) {
    val isEmpty: Boolean get() = events.isEmpty() && repeats.isEmpty() && lastTask == null
}

/** What Mode says after a task, and, when it offers to do something, that task and the two answers to tap. */
public data class NextLine(val line: String, val offer: Offer? = null) {
    /** Yes ([yes], "Yes, look there") runs [task]; no ([no], "Skip it") doesn't. */
    public data class Offer(val task: String, val yes: String, val no: String)
}

/**
 * Mode's first words when it is started by voice ("Hey Siri, start Mode"), written by the AI like a personal
 * assistant would: a short greeting that asks what they need, or, when something in their day is coming up
 * and Mode could help, that one thing offered as a question ("Evening, Alex. Dinner with Priya's at 7. Want
 * me to text her you're on your way?"). Never a fixed line.
 *
 * It must not keep them waiting: [line] gives up after [budgetMs] and returns null, and the caller says its
 * own short greeting.
 */
public class Greeter(
    private val llm: LlmClient,
    private val memory: MemoryRepository?,
    /** The fast model at its lowest effort: the greeting has to come back in about a second. */
    private val model: () -> Pair<String, ModelProvider>?,
    /** The phone Mode runs on, as the prompt names it ("iPhone", "Android phone"). */
    private val device: String = "iPhone",
) {
    /** The model the last line was asked of ("gpt-6-luna@low"), for the log. */
    public var lastModel: String? = null
        private set

    /**
     * The first words, and, when they offer to do something, that task and the two answers: a "yes" to the
     * greeting then runs the offered task (not the word "yes").
     */
    public suspend fun opening(
        name: String?, hour: Int, day: DayContext, budgetMs: Long = BUDGET_MS,
        /** The language to speak ("Hindi"); null: English. */
        language: String? = null,
    ): NextLine? {
        val (id, provider) = model() ?: return null
        lastModel = id
        return try {
            withTimeoutOrNull(budgetMs) {
                val user = buildString {
                    append("THEIR NAME: ").append(name?.trim()?.split(' ')?.firstOrNull()?.ifBlank { null } ?: "(not known)").append('\n')
                    append("NOW: ").append(day.now).append(" (").append(Acknowledger.partOfDay(hour)).append(")\n")
                    if (day.events.isNotEmpty()) append("THEIR CALENDAR FROM NOW:\n").append(day.events.take(EVENTS).joinToString("\n") { "- $it" }).append('\n')
                    if (day.repeats.isNotEmpty()) append("TASKS THEY REPEAT, DUE SOON:\n").append(day.repeats.take(EVENTS).joinToString("\n") { "- $it" }).append('\n')
                    day.lastTask?.let { append("THE LAST THING YOU DID FOR THEM TODAY: ").append(it).append('\n') }
                    val known = memory?.ofKind(MemoryKind.Fact).orEmpty().take(FACTS).map { it.text }
                    if (known.isNotEmpty()) append("WHAT YOU KNOW ABOUT THEM:\n").append(known.joinToString("\n") { "- $it" }).append('\n')
                    if (day.isEmpty) append("NOTHING IS COMING UP: just greet them and ask what they need.\n")
                }
                val system = (language?.let { SYSTEM.replace(LANGUAGE_RULE, "Speak $it.") } ?: SYSTEM).onDevice(device)
                val reply = llm.forProvider(provider).chat(id, listOf(ChatMessage("system", system), ChatMessage("user", user)), NEXT_TOOLS)
                val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments
                fun arg(key: String) = (args?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                clean(arg("line") ?: reply.text)?.let { line ->
                    NextLine(line, arg("offer_task")?.let { t ->
                        NextLine.Offer(t, arg("yes_label")?.take(LABEL_CHARS) ?: "Yes", arg("no_label")?.take(LABEL_CHARS) ?: "Not now")
                    })
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * What Mode says after a task while the conversation carries on: asks if there is anything else, or offers to
     * sort out what didn't work ([finished] false, [result] says what), or one thing coming up in their day.
     * Never repeats the result (it was just said).
     */
    public suspend fun next(
        name: String?, hour: Int, day: DayContext, task: String, finished: Boolean, result: String?,
        budgetMs: Long = BUDGET_MS, language: String? = null,
        /** They just said no to this offer: only ask if there is anything else. */
        declined: String? = null,
    ): NextLine? {
        val (id, provider) = model() ?: return null
        lastModel = id
        return try {
            withTimeoutOrNull(budgetMs) {
                val user = buildString {
                    append("THEIR NAME: ").append(name?.trim()?.split(' ')?.firstOrNull()?.ifBlank { null } ?: "(not known)").append('\n')
                    append("NOW: ").append(day.now).append(" (").append(Acknowledger.partOfDay(hour)).append(")\n")
                    append("THE TASK YOU JUST DID: ").append(task.trim()).append('\n')
                    append("HOW IT WENT: ").append(if (finished) "done" else "not all of it was done").append('\n')
                    result?.trim()?.takeIf { it.isNotEmpty() }?.let { append("WHAT YOU TOLD THEM: ").append(it.take(RESULT_CHARS)).append('\n') }
                    declined?.let { append("THEY JUST SAID NO TO YOUR OFFER: ").append(it).append(". Don't offer it again; ask if there's anything else.\n") }
                    if (day.events.isNotEmpty()) append("THEIR CALENDAR FROM NOW:\n").append(day.events.take(EVENTS).joinToString("\n") { "- $it" }).append('\n')
                    if (day.repeats.isNotEmpty()) append("TASKS THEY REPEAT, DUE SOON:\n").append(day.repeats.take(EVENTS).joinToString("\n") { "- $it" }).append('\n')
                }
                val system = (language?.let { NEXT.replace(LANGUAGE_RULE, "Speak $it.") } ?: NEXT).onDevice(device)
                val reply = llm.forProvider(provider).chat(id, listOf(ChatMessage("system", system), ChatMessage("user", user)), NEXT_TOOLS)
                val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments
                fun arg(key: String) = (args?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                clean(arg("line") ?: reply.text)?.let { line ->
                    val offer = arg("offer_task")?.takeIf { declined == null }?.let { t ->
                        NextLine.Offer(t, arg("yes_label")?.take(LABEL_CHARS) ?: "Yes", arg("no_label")?.take(LABEL_CHARS) ?: "Skip it")
                    }
                    NextLine(line, offer)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    public companion object {
        private const val RESULT_CHARS = 600

        private val NEXT = """
            You are Mode, the user's personal assistant on their iPhone, in a spoken conversation. You just told them
            how a task went; they are still there. Say one short, natural line (at most 24 words) to keep going:
            - If part of it wasn't done, offer to sort out that part, as a question ("Want me to look in your work
              calendar too, or skip it?"). Don't apologise twice, don't repeat the result. Give offer_task (the whole
              task to run if they say yes, standing on its own: "Look for today's DoorDash meeting in my work
              calendar"), yes_label ("Yes, look there") and no_label ("Skip it").
            - Otherwise ask if there's anything else, in your own words, or offer ONE thing coming up soon in their
              day that you could do on their phone (then give offer_task, yes_label, no_label too). Only use what is
              listed; never invent.
            - Plain spoken words: no emoji, no markdown, no quotes. Speak English.
            Call say with the line.
        """.trimIndent()

        /** How long the greeting may take before Mode says its own. */
        public const val BUDGET_MS: Long = 1_800
        private const val EVENTS = 5
        private const val FACTS = 8
        private const val MAX_WORDS = 28
        private const val TOOL = "say"
        private const val LANGUAGE_RULE = "Speak English."

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
            You are Mode, the user's personal assistant on their iPhone. They just called you by voice; you speak
            first, out loud. Say one short, natural line (at most 24 words), like a warm, capable human assistant
            picking up the phone.
            - If something in their day is coming up soon (within about 3 hours) and you could clearly help with it
              on their phone, offer that ONE thing as a question ("Dinner with Priya's at 7. Want me to text her
              you're on your way?"). Only use what is listed; never invent plans, people or times. Whenever the line
              offers to do something, also give offer_task (the whole task to run if they say yes, standing on its
              own: "Text Priya that I'm on my way to dinner"), yes_label ("Yes, text her") and no_label ("Not now").
            - Otherwise greet them and ask what they need, in your own words, different each time.
            - Use their first name now and then, not always. Match the time of day.
            - Plain spoken words: no emoji, no markdown, no quotes, no lists. Speak English.
            Call say with the line.
        """.trimIndent()

        private const val LABEL_CHARS = 24

        private val NEXT_TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL)
                    put("description", "What Mode says next, and what it offers to do, if anything.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("line") { put("type", "string"); put("description", "One short spoken line.") }
                            putJsonObject("offer_task") { put("type", "string"); put("description", "Only when the line offers to do something: that task, in the user's words, standing on its own.") }
                            putJsonObject("yes_label") { put("type", "string"); put("description", "The yes button, 1–3 words (\"Yes, look there\").") }
                            putJsonObject("no_label") { put("type", "string"); put("description", "The no button, 1–3 words (\"Skip it\").") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("line")) }
                    }
                }
            })
        }
    }
}
