package com.piyush.mode.feature.settings.voice

/**
 * Talking over Mode: when the words Apple hears while Mode speaks are the user's, not Mode's own voice coming
 * back, and not a nod. Pure rules, so talking mode can be tested without a microphone:
 * - the user has been speaking at least [MIN_SPEECH_MS];
 * - at least [MIN_NEW_WORDS] recognised words that are not in the line Mode is saying (the echo guard: a
 *   microphone without perfect echo cancellation hears Mode too);
 * - "ok", "haan", "hmm", "yes" and such on their own never interrupt (a backchannel).
 * Research: LiveKit's word-gated interruption (86% precision at 500 ms), OpenAI's semantic VAD, Vapi's stop plan.
 */
public object BargeIn {
    public const val MIN_SPEECH_MS: Long = 250
    public const val MIN_NEW_WORDS: Int = 2
    /** Words that only mean "I'm listening", in English and Hindi. */
    private val BACKCHANNELS = setOf(
        "ok", "okay", "k", "yes", "yeah", "yep", "ya", "yah", "no", "hmm", "hm", "mhm", "mm", "mm-hmm", "uh-huh", "uh", "um", "right", "sure", "fine", "good", "great", "nice", "cool",
        "haan", "ha", "hanji", "acha", "accha", "achha", "theek", "thik", "theek hai", "thik hai", "hmm hmm", "okay okay", "ok ok", "yes yes", "haan haan",
    )
    private val WORD = Regex("""[\p{L}\p{N}']+""")

    /** Whether [heard] (Apple's live words so far) is the user talking over [line], after [speechMs] of speech. */
    public fun shouldInterrupt(heard: String, line: String, speechMs: Long): Boolean {
        if (speechMs < MIN_SPEECH_MS) return false
        if (isBackchannel(heard)) return false
        return newWords(heard, line).size >= MIN_NEW_WORDS
    }

    /** "ok", "haan haan", "mm-hmm": a nod, not an interruption. */
    public fun isBackchannel(heard: String): Boolean {
        val words = words(heard)
        if (words.isEmpty()) return true
        val joined = words.joinToString(" ")
        return joined in BACKCHANNELS || words.all { it in BACKCHANNELS }
    }

    /** The heard words that are not in the line being spoken (Mode's own voice heard back is ignored). */
    public fun newWords(heard: String, line: String): List<String> {
        val spoken = words(line).toSet()
        return words(heard).filter { it !in spoken && it !in BACKCHANNELS }
    }

    private fun words(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()
}
