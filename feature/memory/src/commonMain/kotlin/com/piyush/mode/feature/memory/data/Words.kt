package com.piyush.mode.feature.memory.data

/** Plain word matching for memory: fast, on-device and enough for a few hundred items. */
internal object Words {
    private val STOP = setOf(
        "a", "an", "the", "to", "on", "in", "at", "of", "for", "and", "or", "is", "it", "my", "me", "i", "you",
        "please", "can", "could", "would", "with", "from", "this", "that", "be", "do", "go", "up", "so", "as",
        "by", "then", "now", "just", "into", "your", "our", "its", "are", "was", "will", "also", "pls",
    )

    /** Lower-case words of two or more letters, without filler words or [placeholders]. */
    fun of(text: String): Set<String> =
        Regex("""\[[^\]]*]""").replace(text.lowercase(), " ")
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 2 && it !in STOP }
            .toSet()

    /** How alike two texts are: shared words over all words (0 to 1). */
    fun similarity(a: String, b: String): Double {
        val x = of(a)
        val y = of(b)
        if (x.isEmpty() || y.isEmpty()) return if (normal(a) == normal(b)) 1.0 else 0.0
        return (x intersect y).size.toDouble() / (x union y).size
    }

    fun normal(text: String): String = text.lowercase().replace(Regex("""\s+"""), " ").trim().trimEnd('.')
}
