package com.piyush.mode.core.platform

/** The words on a question's buttons, in the user's language (the app passes them in). */
public data class QuestionWords(
    val yes: String,
    val no: String,
    val always: String,
    val entered: String,
    val reply: String,
    val somethingElse: String,
)

/** One button on a question's notification: what it says and the answer it sends ([typed]: a reply box instead). */
public data class QuestionAction(val title: String, val answer: String, val typed: Boolean = false)

/**
 * The buttons a question gets where it's answered in place (an Android notification): yes/no (and
 * "always"), up to three choices (with a typed "something else" when there are more), a typed reply, or
 * "I've entered it" for something only the user can do. The answers are what the task runner expects.
 */
public object QuestionActions {
    public const val YES: String = "yes"
    public const val NO: String = "no"
    public const val ALWAYS: String = "always"
    public const val DONE: String = "done"
    /** Android shows at most three actions on a notification. */
    public const val MAX: Int = 3

    public fun of(kind: QuestionKind, options: List<String>, words: QuestionWords): List<QuestionAction> = when (kind) {
        QuestionKind.YesNo -> listOf(QuestionAction(words.yes, YES), QuestionAction(words.no, NO))
        QuestionKind.YesNoAlways -> listOf(QuestionAction(words.yes, YES), QuestionAction(words.always, ALWAYS), QuestionAction(words.no, NO))
        QuestionKind.YourTurn -> listOf(QuestionAction(words.entered, DONE))
        QuestionKind.Text -> listOf(QuestionAction(words.reply, "", typed = true))
        QuestionKind.Choice ->
            if (options.size <= MAX) options.map { QuestionAction(it, it) }
            else options.take(MAX - 1).map { QuestionAction(it, it) } + QuestionAction(words.somethingElse, "", typed = true)
    }
}
