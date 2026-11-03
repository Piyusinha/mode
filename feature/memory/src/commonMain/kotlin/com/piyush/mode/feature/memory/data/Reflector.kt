package com.piyush.mode.feature.memory.data

import com.piyush.mode.llm.ModelProvider

import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.memory.domain.SaveResult
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Learns from a finished task (docs/harness.md §4). One call to the reflection
 * model, acting as an outside critic: it sees what was done and the final
 * screen, not the agent's reasons (EvoSkill-GUI found that isolation matters),
 * plus what is already remembered about the same people and apps, and returns
 * changes (Mem0's add / update / forget over existing items).
 *
 * The harness, not the model, has the last word: every change needs evidence;
 * a new skill only comes from a task that succeeded; a failure can only add to
 * a skill, never rewrite it; at most [MAX_CHANGES]; and the repository's secret
 * filter and merging apply to everything.
 */
public class Reflector(
    private val llm: LlmClient,
    private val memory: MemoryRepository,
) {
    public enum class Outcome { Done, GaveUp, Stopped, Failed }

    public class Input(
        public val taskId: String,
        public val task: String,
        public val outcome: Outcome,
        public val summary: String,
        /** One line per step: action and what happened, without the agent's reasons. */
        public val transcript: List<String>,
        public val notes: List<String>,
        public val answers: List<Pair<String, String>>,
        public val appsOpened: List<String>,
        public val finalScreen: ByteArray?,
    )

    public class Reflection(
        /** Facts about the user that were added or changed: shown as "Remembered · Undo". */
        public val remembered: List<MemoryItem>,
        /** Every saved skill and tip too. */
        public val saved: List<MemoryItem>,
        /** Changes the harness refused, and why. */
        public val refused: List<String>,
        public val failureType: String?,
        public val rootCause: String?,
        public val costUsd: Double,
    )

    public suspend fun reflect(input: Input, model: String, provider: ModelProvider = ModelProvider.OpenRouter): Reflection {
        val known = relevantItems(input)
        val reply = llm.forProvider(provider).chat(model, messages(input, known), TOOLS)
        val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments
            ?: return Reflection(emptyList(), emptyList(), listOf("the model gave no changes"), null, null, reply.usage.costUsd)

        val remembered = mutableListOf<MemoryItem>()
        val saved = mutableListOf<MemoryItem>()
        val refused = mutableListOf<String>()
        val changes = (args["changes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        changes.drop(MAX_CHANGES).forEach { refused += "over the limit of $MAX_CHANGES: ${it.str("title")}" }
        for (change in changes.take(MAX_CHANGES)) {
            when (val result = apply(change, input, known)) {
                is Applied.Saved -> {
                    saved += result.item
                    if (result.item.kind == MemoryKind.Fact && result.changed) remembered += result.item
                }
                is Applied.Refused -> refused += result.why
                Applied.Nothing -> Unit
            }
        }
        return Reflection(
            remembered, saved, refused,
            failureType = args.str("failure_type").takeIf { it.isNotEmpty() && it != "none" },
            rootCause = args.str("root_cause").ifEmpty { null },
            costUsd = reply.usage.costUsd,
        )
    }

    private sealed interface Applied {
        data class Saved(val item: MemoryItem, val changed: Boolean) : Applied
        data class Refused(val why: String) : Applied
        data object Nothing : Applied
    }

    private fun apply(change: JsonObject, input: Input, known: List<MemoryItem>): Applied {
        val op = change.str("op")
        val title = change.str("title")
        val evidence = change.str("evidence")
        if (op == "noop") return Applied.Nothing
        if (evidence.isBlank()) return Applied.Refused("no evidence: $title")
        val kind = MemoryKind.entries.firstOrNull { it.stored == change.str("kind") }
            ?: return Applied.Refused("unknown kind '${change.str("kind")}': $title")
        val succeeded = input.outcome == Outcome.Done

        // What a list shows today (ratings, prices, delivery times) goes out of date: seen on the phone, a
        // restaurant's rating and price were kept as a fact about the user, against the prompt's rule.
        if (kind == MemoryKind.Fact && op != "forget" && LIST_DATA.containsMatchIn(change.str("text"))) {
            return Applied.Refused("today's list data, not a lasting fact: $title")
        }
        when (op) {
            "add" -> {
                if (kind == MemoryKind.Skill && !succeeded) return Applied.Refused("a skill only comes from a task that worked: $title")
                val draft = MemoryDraft(
                    kind = kind,
                    subject = change.str("subject"),
                    title = title,
                    text = change.str("text"),
                    evidence = evidence,
                    keywords = (change["keywords"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                    app = change.str("app").ifEmpty { null },
                )
                return when (val r = memory.save(draft, input.taskId)) {
                    is SaveResult.Added -> Applied.Saved(r.item, changed = true)
                    is SaveResult.Updated -> Applied.Saved(r.item, changed = true)
                    is SaveResult.Unchanged -> Applied.Saved(r.item, changed = false)
                    is SaveResult.Rejected -> Applied.Refused("${r.why}: $title")
                }
            }
            "update" -> {
                val item = known.firstOrNull { it.id == change.str("id") } ?: return Applied.Refused("no such item to update: $title")
                val text = change.str("text")
                // After a failure a skill may only gain lines ("If it goes wrong: …"), never lose its route.
                if (item.kind == MemoryKind.Skill && !succeeded && !text.contains(item.text.trim())) {
                    return Applied.Refused("a failed task can only add to a skill: ${item.title}")
                }
                val updated = memory.update(item.id, text, evidence, input.taskId) ?: return Applied.Refused("looks like a secret: ${item.title}")
                return Applied.Saved(updated, changed = true)
            }
            "forget" -> {
                val item = known.firstOrNull { it.id == change.str("id") } ?: return Applied.Refused("no such item to forget: $title")
                memory.forget(item.id)
                return Applied.Nothing
            }
            else -> return Applied.Refused("unknown op '$op': $title")
        }
    }

    /** What is already known that this task touched: every fact, and skills and tips for its apps and words. */
    private fun relevantItems(input: Input): List<MemoryItem> {
        val words = Words.of(input.task + " " + input.appsOpened.joinToString(" "))
        val apps = input.appsOpened.map { it.lowercase() }.toSet()
        return memory.all().filter { item ->
            item.kind == MemoryKind.Fact ||
                item.app?.lowercase() in apps || item.subject in apps ||
                (Words.of("${item.title} ${item.keywords.joinToString(" ")}") intersect words).isNotEmpty()
        }.take(MAX_KNOWN)
    }

    private fun messages(input: Input, known: List<MemoryItem>): List<ChatMessage> {
        val user = buildString {
            append("THE TASK: ").append(input.task).append('\n')
            append("HOW IT ENDED: ").append(input.outcome.name).append(" — ").append(input.summary).append("\n\n")
            if (input.answers.isNotEmpty()) {
                append("THE USER'S ANSWERS:\n")
                input.answers.forEach { (q, a) -> append("- Asked: ").append(q).append(" / They said: ").append(a).append('\n') }
                append('\n')
            }
            append("WHAT WAS DONE, step by step:\n")
            input.transcript.takeLast(40).forEach { append(it.take(300)).append('\n') }
            if (input.notes.isNotEmpty()) {
                append("\nFACTS NOTED ON THE WAY:\n")
                input.notes.forEach { append("- ").append(it).append('\n') }
            }
            if (input.appsOpened.isNotEmpty()) append("\nAPPS OPENED: ").append(input.appsOpened.joinToString(", ")).append('\n')
            append("\nALREADY REMEMBERED (refer to these by id to update or forget):\n")
            if (known.isEmpty()) append("(nothing yet)\n")
            known.forEach { append("- id=").append(it.id).append(" [").append(it.kind.stored).append("] ").append(it.title).append(": ").append(it.text.replace('\n', ' ')).append('\n') }
            append("\nThe screenshot is the final screen. Decide what to remember and call save_memory.")
        }
        return listOf(ChatMessage("system", SYSTEM), ChatMessage("user", user, input.finalScreen))
    }

    private fun JsonObject.str(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    public companion object {
        public const val MAX_CHANGES: Int = 5

        /** Ratings, prices and delivery times: what a list shows today. */
        private val LIST_DATA = Regex(
            """\b(rated|ratings?)\s*\d|\d(\.\d)?\s*(stars?|★)|(₹|\brs\.?|\binr)\s*\d|\$\s*\d|\d+\s*-\s*\d+\s*mins?\b|\bdelivery (time|in)\b""",
            RegexOption.IGNORE_CASE,
        )
        private const val MAX_KNOWN = 40
        private const val TOOL = "save_memory"

        private val SYSTEM = """
            You review one finished task that an assistant called Mode did on the user's iPhone, as an outside
            critic, and decide what Mode should remember to do better next time. You see what was done and the
            final screen, not the assistant's reasoning.

            Three kinds of memory:
            - fact: about the user, stated by the task, their answers or the screen. People ("Asha: message
              her on WhatsApp"), preferences ("likes short, casual messages"), places ("Home: Indiranagar,
              Bengaluru"). subject = the person or topic, lower case. Never guess or infer beyond the evidence.
            - skill: a reusable route for this kind of task, only when the task succeeded. Title with
              placeholders ("Message [Person] on WhatsApp", "Turn off Dark Mode"); text = short numbered steps
              that worked, without the detours, plus "If it goes wrong:" lines when useful. subject = the title
              in lower case; keywords = 4 to 10 words someone might use to ask for it (synonyms too); app = the
              main app.
            - tip: one short note about an app's layout or behaviour that would have saved steps ("Calendar:
              the list button is top right; the icon next to it opens search"). Above all a shortcut the
              assistant missed or found: a recent or suggested entry to tap instead of typing ("Rapido: recent
              destinations are listed on the home screen; tap one instead of searching"), a sort or filter
              option, a direct button. subject = the app, lower case.

            Rules:
            - At most 5 changes. Fewer is better: only what will clearly help another task.
            - Every change needs evidence: the step or answer it comes from.
            - Prefer updating an item already remembered (by its id) over adding a near-duplicate; forget one
              the task proved wrong.
            - If the task found a shorter way than a remembered skill's steps (a recent entry instead of typing,
              a sort instead of scrolling), update that skill's steps to the shorter way.
            - Quote on-screen labels in a skill's steps exactly as shown ("Tap 'Log' beside the meal"): Mode
              finds them on screen next time and points at them.
            - If the task failed, say why in failure_type and root_cause; add tips about what to avoid, or add
              "If it goes wrong:" lines to an existing skill. Never make a new skill from a failure.
            - Never store passwords, passcodes, one-time codes, card or bank numbers, or ID numbers.
            - Never store what is on a list today: prices, ratings, offers, availability, delivery times, which
              items or restaurants were shown. It goes out of date. Keep how to get things done in an app, and
              lasting facts and preferences about the user.
            - Nothing worth keeping? Call save_memory with no changes.
        """.trimIndent()

        private val TOOLS: JsonArray = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", TOOL)
                        put("description", "Save what Mode should remember from this task.")
                        putJsonObject("parameters") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("failure_type") {
                                    put("type", "string")
                                    putJsonArray("enum") {
                                        listOf("none", "planning_gap", "locating_error", "verification_miss", "app_problem", "blocked", "other")
                                            .forEach { add(JsonPrimitive(it)) }
                                    }
                                    put("description", "Why it failed, or none if it worked.")
                                }
                                putJsonObject("root_cause") { put("type", "string"); put("description", "One sentence, when it failed.") }
                                putJsonObject("changes") {
                                    put("type", "array")
                                    putJsonObject("items") {
                                        put("type", "object")
                                        putJsonObject("properties") {
                                            putJsonObject("op") { put("type", "string"); putJsonArray("enum") { listOf("add", "update", "forget", "noop").forEach { add(JsonPrimitive(it)) } } }
                                            putJsonObject("kind") { put("type", "string"); putJsonArray("enum") { listOf("fact", "skill", "tip").forEach { add(JsonPrimitive(it)) } } }
                                            putJsonObject("id") { put("type", "string"); put("description", "For update or forget: the remembered item's id.") }
                                            putJsonObject("subject") { put("type", "string") }
                                            putJsonObject("title") { put("type", "string") }
                                            putJsonObject("text") { put("type", "string") }
                                            putJsonObject("keywords") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                                            putJsonObject("app") { put("type", "string") }
                                            putJsonObject("evidence") { put("type", "string") }
                                        }
                                        putJsonArray("required") { listOf("op", "kind", "title", "evidence").forEach { add(JsonPrimitive(it)) } }
                                    }
                                }
                            }
                            putJsonArray("required") { listOf("failure_type", "changes").forEach { add(JsonPrimitive(it)) } }
                        }
                    }
                },
            )
        }
    }
}
