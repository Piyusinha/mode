package com.piyush.mode.feature.settings.voice

/**
 * Talking over Mode: when the words Apple hears while Mode speaks are the user's, not Mode's own voice coming
 * back, and not a nod. Pure rules, so talking mode can be tested without a microphone:
 * - the user has been speaking at least [MIN_SPEECH_MS];
 * - of the last [RECENT_WORDS] words heard, at least [MIN_NEW_WORDS] are not in the line Mode is saying (the echo
 *   guard: what echo cancellation leaves of Mode's voice comes back as its own words, often misheard, so a word
 *   close to one in the line — "radar" for "Flightradar", "harbor" for "Harper", "check" for "checking" — counts
 *   as Mode's, and so do one- and two-letter words). Someone talking over Mode says several new words in a row;
 *   a misheard echo scatters one here and there;
 * - "ok", "haan", "hmm", "yes" and such on their own never interrupt (a backchannel).
 * Research: LiveKit's word-gated interruption (86% precision at 500 ms), OpenAI's semantic VAD, Vapi's stop plan.
 */
public object BargeIn {
    public const val MIN_SPEECH_MS: Long = 500
    public const val MIN_NEW_WORDS: Int = 3
    public const val RECENT_WORDS: Int = 5
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
        val spoken = words(line)
        return words(heard).takeLast(RECENT_WORDS).count { isNew(it, spoken) } >= MIN_NEW_WORDS
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
        val spoken = words(line)
        return words(heard).filter { isNew(it, spoken) }
    }

    /** A heard word that is neither a nod nor (nearly) one of the words Mode is saying. */
    private fun isNew(word: String, spoken: List<String>): Boolean =
        word.length > 2 && word !in BACKCHANNELS && spoken.none { echoOf(word, it) }

    /** [heard] is how the microphone may have caught Mode's [said]: the same, part of it, or one or two letters off. */
    private fun echoOf(heard: String, said: String): Boolean {
        if (heard == said) return true
        if (heard.length >= 3 && said.length >= 4 && (said.contains(heard) || heard.contains(said))) return true
        val allowed = when {
            minOf(heard.length, said.length) >= 7 -> 2
            minOf(heard.length, said.length) >= 4 -> 1
            else -> 0
        }
        return allowed > 0 && kotlin.math.abs(heard.length - said.length) <= allowed && distance(heard, said) <= allowed
    }

    /** Levenshtein distance (short words only). */
    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun words(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()
}
