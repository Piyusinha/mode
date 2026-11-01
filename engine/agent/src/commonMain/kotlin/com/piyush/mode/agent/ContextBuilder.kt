package com.piyush.mode.agent

import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.ChatMessage.Part

/**
 * Builds what the model sees each step, in fixed sections with a budget each
 * (docs/harness.md §5). Parts that stay the same from step to step come first and
 * end with a cache point, so a provider can reuse them:
 *
 * 1. system: the rules (never trimmed) · cache point
 * 2. stable: about you, skills, app tips, recent tasks, the conversation so far, the task,
 *    answers · cache point
 * 3. per step: step number, notes, recent steps, what the last action was expected to do,
 *    the plan (recited last, where attention is highest, and so it never breaks the cache),
 *    the screen's text, then the screenshot
 */
object ContextBuilder {
    const val MAX_ABOUT_YOU = 25
    const val MAX_APP_NOTES = 10
    const val MAX_SKILL_CHARS = 1500
    const val MAX_RECENT_TASKS = 5
    const val MAX_EARLIER_TURNS = 3
    const val MAX_TURN_CHARS = 300
    const val MAX_ANSWERS = 5
    const val MAX_PLAN_CHARS = 1500
    const val MAX_NOTES = 30
    const val MAX_NOTE_CHARS = 200
    const val MAX_TEXTS = 80

    /** The screen as the model will see it. */
    class Screen(
        val jpeg: ByteArray,
        val imageWidth: Int,
        val imageHeight: Int,
        /** Text with its centre in screenshot pixels. */
        val texts: List<Triple<String, Int, Int>>,
        /** The keyboard is up: a field is focused (its keys are left out of [texts]). */
        val keyboardUp: Boolean = false,
    )

    class Input(
        val systemPrompt: String,
        val task: String,
        /** The brain's brief for this task, if it wrote one. */
        val brief: String? = null,
        val context: TaskContext,
        val step: Int,
        val maxSteps: Int,
        val answers: List<Pair<String, String>>,
        val plan: String?,
        val notes: List<String>,
        /** Every step so far, one line each, oldest first. */
        val history: List<String>,
        /** How many recent steps are shown word for word. */
        val recentSteps: Int,
        val lastApp: String?,
        /** What the model said its last action would do, shown once so it can check. */
        val expectation: String? = null,
        /** Mode's record of what was changed for the user, with proof; never summarised away. */
        val done: List<String> = emptyList(),
        /** Every line of text seen while scrolling the current list, in order, so options can be compared. */
        val seenWhileScrolling: List<String> = emptyList(),
        val screen: Screen,
        /** Mode's own screen is in front: describe it instead of showing it. */
        val hideScreen: Boolean,
    )

    fun build(input: Input): List<ChatMessage> = listOf(
        ChatMessage("system", listOf(Part.Text(input.systemPrompt, cacheAfter = true))),
        ChatMessage(
            "user",
            buildList {
                add(Part.Text(stable(input), cacheAfter = true))
                add(Part.Text(perStep(input)))
                if (!input.hideScreen) add(Part.Image(input.screen.jpeg))
            },
        ),
    )

    /** Changes only when an answer arrives. */
    fun stable(input: Input): String = buildString {
        val c = input.context
        val about = listOfNotNull(c.userName?.let { "Their name: $it" }) + c.aboutYou
        if (about.isNotEmpty()) {
            append("ABOUT THE USER:\n")
            about.take(MAX_ABOUT_YOU).forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (c.skills.isNotEmpty()) {
            append("WHAT WORKED BEFORE (follow it when it fits; the screen has the last word):\n")
            append(c.skills.joinToString("\n").clip(MAX_SKILL_CHARS)).append("\n\n")
        }
        if (c.appNotes.isNotEmpty()) {
            append("TIPS FOR THESE APPS (from earlier tasks):\n")
            c.appNotes.take(MAX_APP_NOTES).forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (c.recentTasks.isNotEmpty()) {
            append("RECENT TASKS (newest first; for \"do that again\" or \"turn it back on\"):\n")
            c.recentTasks.take(MAX_RECENT_TASKS).forEach { append("- ").append(it.clip(MAX_TURN_CHARS)).append('\n') }
            append('\n')
        }
        if (c.earlierTurns.isNotEmpty()) {
            append("EARLIER IN THIS CONVERSATION (context only; the task below is what to do now):\n")
            c.earlierTurns.takeLast(MAX_EARLIER_TURNS).forEach {
                append("- They asked: ").append(it.task.clip(MAX_TURN_CHARS)).append('\n')
                append("  Result: ").append(it.outcome.clip(MAX_TURN_CHARS)).append('\n')
            }
            append('\n')
        }
        append("TASK: ").append(input.task).append('\n')
        input.brief?.let { append("\nTHE BRIEF (from your planning mind; follow it):\n").append(it).append('\n') }
        if (input.answers.isNotEmpty()) {
            append("\nTHE USER'S ANSWERS (final: act on them, never ask about the same thing again):\n")
            input.answers.takeLast(MAX_ANSWERS).forEach { (q, a) ->
                append("- You asked: ").append(q).append("\n  They said: ").append(a).append('\n')
            }
        }
    }

    /** Changes every step. */
    fun perStep(input: Input): String = buildString {
        append("STEP ").append(input.step).append(" of at most ").append(input.maxSteps).append("\n\n")
        if (input.done.isNotEmpty()) {
            append("DONE SO FAR (Mode's record of what you changed for the user; trust it over how the screen looks now, and never repeat a confirmed one):\n")
            input.done.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (input.notes.isNotEmpty()) {
            append("YOUR NOTES (facts you kept; use them in your final summary):\n")
            val hidden = input.notes.size - MAX_NOTES
            if (hidden > 0) append("(").append(hidden).append(" earlier notes not shown)\n")
            input.notes.takeLast(MAX_NOTES).forEach { append("- ").append(it.clip(MAX_NOTE_CHARS)).append('\n') }
            append('\n')
        }
        if (input.history.isEmpty()) {
            append("No actions yet.\n\n")
        } else {
            append("WHAT HAPPENED SO FAR (latest last):\n")
            val earlier = input.history.size - input.recentSteps
            if (earlier > 0) append(summarise(input.history.take(earlier))).append('\n')
            input.history.takeLast(input.recentSteps).forEach { append(it).append('\n') }
            append('\n')
        }
        input.lastApp?.let { append("App you last opened: ").append(it).append("\n\n") }
        if (input.seenWhileScrolling.isNotEmpty()) {
            append("SEEN WHILE SCROLLING THIS LIST (everything that was on screen, top to bottom; compare and choose from this, no need to scroll back):\n")
            input.seenWhileScrolling.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        input.expectation?.let {
            append("YOU EXPECTED your last action to: ").append(it.clip(MAX_NOTE_CHARS))
            append(". Does the screen below show that? If not, deal with it before moving on.\n\n")
        }
        input.plan?.let {
            append("YOUR PLAN (keep it up to date with the plan tool):\n").append(it.clip(MAX_PLAN_CHARS)).append("\n\n")
        }
        if (input.hideScreen) {
            append("THE SCREEN NOW: Mode, the app that runs you, is in front (not shown: it is not part of the task). ")
            append("Start by opening the app or place the task needs (usually open_app), or plan first if the task has several parts.")
            return@buildString
        }
        val s = input.screen
        if (s.keyboardUp) {
            append("KEYBOARD IS UP: a text field is focused and ready. Type with type_text now; do not tap the field again. ")
            append("(Its keys are left out of the text below.)\n")
        }
        append("THE SCREEN NOW: the screenshot is ${s.imageWidth}×${s.imageHeight} pixels. ")
        append("Text on it, with each piece's centre in screenshot pixels:\n")
        if (s.texts.isEmpty()) append("(no text found)\n")
        s.texts.take(MAX_TEXTS).forEach { (text, x, y) -> append("\"").append(text).append("\" at (").append(x).append(", ").append(y).append(")\n") }
        append("\nChoose the next single action.")
    }

    /**
     * One line for the steps no longer shown word for word, made without a model
     * call: "Steps 1–9 (earlier): opened Settings, WhatsApp; 6 taps, 1 typed; 2 left the screen unchanged."
     */
    fun summarise(lines: List<String>): String {
        val actions = lines.map { it.substringAfter(". ").substringBefore(" — ") }
        val apps = actions.filter { it.startsWith("open ") }.map { it.removePrefix("open ") }.distinct()
        val counts = listOf(
            "tap" to actions.count { it.startsWith("tap ") || it.startsWith("long press") },
            "typed" to actions.count { it.startsWith("type ") },
            "scroll" to actions.count { it.startsWith("scroll") },
            "swipe" to actions.count { it == "swipe" },
            "wait" to actions.count { it.startsWith("wait") },
        ).filter { it.second > 0 }.joinToString(", ") { (what, n) ->
            if (what == "typed") "$n typed" else "$n $what" + if (n == 1) "" else "s"
        }
        val unchanged = lines.count { "screen did NOT change" in it }
        val first = lines.first().substringBefore('.')
        val last = lines.last().substringBefore('.')
        return buildString {
            append("Steps ").append(first).append('–').append(last).append(" (earlier): ")
            val parts = listOfNotNull(
                apps.takeIf { it.isNotEmpty() }?.let { "opened " + it.joinToString(", ") },
                counts.ifEmpty { null },
                unchanged.takeIf { it > 0 }?.let { "$it left the screen unchanged" },
            )
            append(parts.ifEmpty { listOf("no phone actions") }.joinToString("; ")).append('.')
        }
    }

    private fun String.clip(max: Int) = if (length <= max) this else take(max - 1) + "…"
}
