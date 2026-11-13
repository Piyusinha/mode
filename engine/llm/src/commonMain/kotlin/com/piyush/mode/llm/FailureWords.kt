package com.piyush.mode.llm

/**
 * What the user reads when the AI call failed: plain words about what to do, never a model id or an HTTP
 * status (those stay in the log and on What went wrong).
 */
object FailureWords {
    const val KEY: String = "Your AI didn’t accept its key. Check it in Settings."
    const val CREDIT: String = "Your AI account is out of credit. Add some, or pick another AI in Settings."
    const val BUSY: String = "Your AI is busy right now. Try again in a minute."
    const val UNREACHABLE: String = "Mode couldn’t reach your AI. Check your connection and try again."
    const val OTHER: String = "Your AI couldn’t answer. What went wrong has the details."

    /** The plain line for a failed AI call; null when [e] isn't one (its own message is already plain). */
    fun of(e: Throwable): String? {
        if (e !is LlmFailed) return null
        return when (e.status) {
            401, 403 -> KEY
            402 -> CREDIT
            429 -> BUSY
            0, in SERVER_ERRORS -> if (e.status == 0 && e.message?.contains("key", ignoreCase = true) == true) e.message else UNREACHABLE
            else -> OTHER
        }
    }

    private val SERVER_ERRORS = 500..599
}
