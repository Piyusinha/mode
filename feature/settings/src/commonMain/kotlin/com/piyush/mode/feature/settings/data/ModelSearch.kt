package com.piyush.mode.feature.settings.data

import com.piyush.mode.feature.settings.domain.ModelOption

/**
 * Finding a model the way people type its name. A plain "contains" missed "anthropic haiku" (the maker
 * is only in the id), "haiku5.5" (no space) and "sonnet 5" (one word in a different place). Every word
 * typed must start a word of the model's id, name or maker; the closest names come first.
 */
public object ModelSearch {

    /** The maker in a model id: "anthropic" in "anthropic/claude-haiku-5.5". */
    public fun maker(option: ModelOption): String = option.id.substringBefore('/', missingDelimiterValue = "")

    /** [options] matching [query] (and [maker], if given), the best match first. */
    public fun search(options: List<ModelOption>, query: String, maker: String? = null): List<ModelOption> {
        val wanted = words(query)
        val pool = options.filter { maker == null || maker(it).equals(maker, ignoreCase = true) }
        if (wanted.isEmpty()) return pool
        val name = words(query).joinToString(" ")
        return pool.mapNotNull { option ->
            val own = words("${option.id} ${option.name} ${maker(option)}")
            if (!wanted.all { w -> own.any { it.startsWith(w) } }) return@mapNotNull null
            val shown = words(option.name).joinToString(" ")
            val score = when {
                shown == name -> 3
                shown.startsWith(name) -> 2
                wanted.all { w -> own.any { it == w } } -> 1
                else -> 0
            }
            // Words of the name the query did not ask for: "sonnet 5" is closer to Sonnet 5 than to Sonnet 5.5.
            val left = words(option.name).toMutableList().apply { wanted.forEach { remove(it) } }.size
            Ranked(option, score, left, if (option.tag != null) 1 else 0)
        }.sortedWith(
            compareByDescending<Ranked> { it.score }.thenBy { it.left }.thenByDescending { it.picked }.thenBy { it.option.costPerTaskUsd },
        ).map { it.option }
    }

    private class Ranked(val option: ModelOption, val score: Int, val left: Int, val picked: Int)

    /** Lower case words, punctuation as spaces, letters split from digits: "Haiku5.5" → haiku, 5, 5. */
    internal fun words(text: String): List<String> =
        text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .replace(Regex("""(?<=\p{L})(?=\p{N})|(?<=\p{N})(?=\p{L})"""), " ")
            .split(' ').filter { it.isNotEmpty() }
}
