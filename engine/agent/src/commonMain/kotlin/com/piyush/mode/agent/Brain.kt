package com.piyush.mode.agent

import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The brain: a strong model that reads the request the way a personal assistant
 * would, before any tap, and writes a brief for the hands (the model that taps).
 * One text-only call. Seen on the phone, every failed run failed on judgement:
 * "talk with Sonali" asked what to say instead of reading the chat; "Beast Life in
 * my snacks" used yesterday's diary; Swiggy reopened on an old restaurant page.
 */
class Brain(private val llm: LlmClient, private val model: String) {

    /** What the task really asks for, and how the hands should go about it. */
    data class Brief(
        val intent: String,
        val app: String?,
        /** "fresh": start the app on its home page; "continue": pick up where it is. */
        val start: String,
        val doneWhen: String,
        val gather: List<String>,
        val decide: List<String>,
        val ask: List<String>,
        val steps: List<String>,
        val report: String,
    ) {
        val fresh: Boolean get() = start != "continue"

        /** As the hands read it. */
        fun text(): String = buildString {
            append("Intent: ").append(intent).append('\n')
            app?.let { append("App: ").append(it).append(" (start: ").append(start).append(")\n") }
            append("Done when: ").append(doneWhen).append('\n')
            if (gather.isNotEmpty()) append("Gather first: ").append(gather.joinToString("; ")).append('\n')
            if (decide.isNotEmpty()) append("Decide yourself: ").append(decide.joinToString("; ")).append('\n')
            if (ask.isNotEmpty()) append("Ask the user (before changing anything): ").append(ask.joinToString("; ")).append('\n')
            append("Steps:\n").append(steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")).append('\n')
            append("Report: ").append(report)
        }
    }

    class Result(val brief: Brief?, val costUsd: Double, val error: String? = null)

    suspend fun brief(task: String, context: TaskContext): Result {
        val messages = listOf(
            ChatMessage("system", listOf(ChatMessage.Part.Text(SYSTEM, cacheAfter = true))),
            ChatMessage("user", listOf(ChatMessage.Part.Text(input(task, context)))),
        )
        return try {
            val reply = llm.chat(model, messages, TOOLS)
            val args = reply.toolCall?.takeIf { it.name == "brief" }?.arguments
            Result(args?.let(::parse), reply.usage.costUsd, if (args == null) "no brief in the reply" else null)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result(null, 0.0, e.message)
        }
    }

    class Decision(val answer: String?, val costUsd: Double, val error: String? = null)

    /**
     * A choice the hands can't make well alone (seen on the phone: the highest-rated restaurant was on
     * screen and Haiku re-planned nine times instead of picking it). Text only: the brief, what was seen
     * while scrolling, the screen's text.
     */
    suspend fun decide(question: String, task: String, brief: String?, seen: List<String>, screen: List<String>): Decision {
        val input = buildString {
            append("REQUEST: ").append(task).append("\n\n")
            brief?.let { append("THE BRIEF YOU WROTE:\n").append(it).append("\n\n") }
            if (seen.isNotEmpty()) append("SEEN WHILE SCROLLING (top to bottom):\n").append(seen.joinToString("\n") { "- $it" }).append("\n\n")
            append("ON SCREEN NOW:\n").append(screen.joinToString("\n") { "- $it" }).append("\n\n")
            append("THE ASSISTANT ASKS: ").append(question)
        }
        val messages = listOf(
            ChatMessage("system", listOf(ChatMessage.Part.Text(DECIDE, cacheAfter = true))),
            ChatMessage("user", listOf(ChatMessage.Part.Text(input))),
        )
        return try {
            val reply = llm.chat(model, messages, DECIDE_TOOLS)
            val answer = reply.toolCall?.takeIf { it.name == "decision" }?.arguments?.let { text(it, "answer") }
                ?: reply.text.trim().takeIf { it.isNotEmpty() }
            Decision(answer, reply.usage.costUsd, if (answer == null) "no decision in the reply" else null)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Decision(null, 0.0, e.message)
        }
    }

    private fun input(task: String, c: TaskContext): String = buildString {
        c.today?.let { append("NOW: ").append(it).append("\n\n") }
        val about = listOfNotNull(c.userName?.let { "Their name: $it" }) + c.aboutYou
        if (about.isNotEmpty()) append("ABOUT THE USER:\n").append(about.joinToString("\n") { "- $it" }).append("\n\n")
        if (c.skills.isNotEmpty()) append("WHAT WORKED BEFORE:\n").append(c.skills.joinToString("\n")).append("\n\n")
        // Learned from earlier tasks ("Rapido: recent destinations are on the home screen"): shape the plan with them.
        if (c.appNotes.isNotEmpty()) {
            append("TIPS FOR THESE APPS (from earlier tasks):\n").append(c.appNotes.take(ContextBuilder.MAX_APP_NOTES).joinToString("\n") { "- $it" }).append("\n\n")
        }
        if (c.recentTasks.isNotEmpty()) append("RECENT TASKS:\n").append(c.recentTasks.joinToString("\n") { "- $it" }).append("\n\n")
        if (c.earlierTurns.isNotEmpty()) {
            append("EARLIER IN THIS CONVERSATION:\n")
            c.earlierTurns.forEach { append("- They asked: ").append(it.task).append("\n  Result: ").append(it.outcome).append('\n') }
            append('\n')
        }
        append("REQUEST: ").append(task)
    }

    companion object {
        private fun list(o: JsonObject, name: String): List<String> =
            (o[name] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }

        private fun text(o: JsonObject, name: String): String? = o[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

        /** The brief from the brain's tool call, or null when it lacks what the hands need. */
        fun parse(o: JsonObject): Brief? {
            val intent = text(o, "intent") ?: return null
            val steps = list(o, "steps").take(MAX_STEPS)
            if (steps.isEmpty()) return null
            return Brief(
                intent = intent,
                app = text(o, "app"),
                start = text(o, "start")?.lowercase()?.takeIf { it == "fresh" || it == "continue" } ?: "fresh",
                doneWhen = text(o, "done_when") ?: "the request is done and visible on screen",
                gather = list(o, "gather"),
                decide = list(o, "decide"),
                ask = list(o, "ask"),
                steps = steps,
                report = text(o, "report") ?: "what you did, briefly",
            )
        }

        const val MAX_STEPS = 4

        private val DECIDE_TOOLS: JsonArray = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", "decision")
                        put("description", "Your decision for the assistant operating the phone.")
                        putJsonObject("parameters") {
                            put("type", "object")
                            putJsonObject("properties") {
                                put(
                                    "answer",
                                    buildJsonObject {
                                        put("type", "string")
                                        put("description", "One or two short sentences: the choice and why in a few words, then exactly what to tap (quote its text as it appears).")
                                    },
                                )
                            }
                            putJsonArray("required") { add(JsonPrimitive("answer")) }
                        }
                    }
                },
            )
        }

        val DECIDE = """
            You are the planning mind of Mode, a personal assistant operating the user's iPhone. The assistant
            doing the tapping asks you to make a choice. Decide from what it saw (the text below; you do not see
            images), following your brief: ignore ads and sponsored entries (AD, Ad, Sponsored, Promoted) and
            entries that are not what was asked; break ties as the brief says (more ratings, then faster or
            nearer). If what you need is not in the text, say exactly what to look at next instead. Answer with
            the decision tool: the choice, why in a few words, and the exact on-screen text to tap.
        """.trimIndent()

        private fun arr(name: String, about: String) = name to buildJsonObject {
            put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", about)
        }

        private fun str(name: String, about: String) = name to buildJsonObject { put("type", "string"); put("description", about) }

        private val TOOLS: JsonArray = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", "brief")
                        put("description", "The brief for the assistant that will operate the phone.")
                        putJsonObject("parameters") {
                            put("type", "object")
                            val props = listOf(
                                str("intent", "What the user really wants, in one sentence, as a good assistant reads it."),
                                str("app", "The app to use, by its name, if one is needed."),
                                str("start", "\"fresh\" to start the app on its home page (a new task), or \"continue\" to pick up where it is."),
                                str("done_when", "What will be visible on screen when it is done."),
                                arr("gather", "What to read or check before acting (the chat so far, the date shown, the list)."),
                                arr("decide", "Choices the user handed over: decide them, and how (break ties)."),
                                arr("ask", "Choices that are the user's (which dish, size, quantity): ask once, with options, before changing anything. Empty if none."),
                                arr("steps", "2 to 4 short steps, each a result to reach."),
                                str("report", "What to tell the user at the end."),
                            )
                            putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
                            putJsonArray("required") { listOf("intent", "start", "done_when", "steps", "report").forEach { add(JsonPrimitive(it)) } }
                        }
                    }
                },
            )
        }

        val SYSTEM = """
            You are the planning mind of Mode, a personal assistant that operates the user's iPhone. Another
            model does the tapping. Before it starts, read the request the way a thoughtful personal assistant
            would and write it a brief by calling the brief tool. You do not see the screen.

            Read the request as the user means it:
            - Spoken requests are often misheard or clipped: work out the plain meaning ("Mood shot open the
              Swiggy app" means open Swiggy). Brand and app names may be misspelt; use the right one.
            - Dates and times mean today unless they say otherwise. Apps reopen where they were left (yesterday's
              diary, an old search, a restaurant from before), so a new task starts the app fresh.
            - "Talk to", "chat with", "reply to" someone means: read the conversation, see what is unanswered
              or where it is going, and continue it in the user's own voice (their language mix, length, tone,
              emoji), not ask the user what to say. Mode shows the user the message before it is sent.
            - "Add" or "log" means a new entry, even if a similar one exists.
            - What the user hands over is yours to decide ("best", "highest rated", "cheapest", the obvious
              match): say how, including how to break ties (more ratings, then faster or nearer). Use the app's
              own search, sort and filter rather than scrolling long lists.
            - What is theirs to choose (which dish or product, size, quantity, a time slot) when several fit:
              put it in ask, to be asked once with options before anything is added. Use what you know about
              them first (ABOUT THE USER, their stated preferences); if they said "any" or "you choose", decide.
            - Never invent facts, names or amounts. What is remembered about lists (ratings, prices) is out of
              date: the screen decides.

            The steps: few and concrete, each a result to reach ("Open Sonali's chat", "Read her last messages
            and his replies", "Write and send the reply"), not single taps. Keep every field to a few words; leave
            out what the hands already know (open the app, dismiss popups). Say what to reach, not how: "Set the
            drop to Bellandur", not "Search for Bellandur", because the app may already list it (a recent or
            suggested entry) and the hands take the quickest way they see. done_when: something visible.
        """.trimIndent()
    }
}
