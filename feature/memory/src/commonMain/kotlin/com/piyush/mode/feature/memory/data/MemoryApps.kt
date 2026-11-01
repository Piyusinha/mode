package com.piyush.mode.feature.memory.data

import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind

/**
 * Tips and routines, grouped by the app they belong to, for the Memory screens. The app is not
 * always stored the same way: in `app` ("MyFitnessPal"), or only as a tip's subject ("myfitnesspal"),
 * often with the name again at the start of the title ("MyFitnessPal: check the diary first").
 */
public object MemoryApps {

    public class Group(
        /** The app's name as best known: "MyFitnessPal". */
        public val name: String,
        public val tips: List<MemoryItem>,
        public val routines: List<MemoryItem>,
    )

    /** Every app with tips or routines, the one with the most first. */
    public fun of(items: List<MemoryItem>): List<Group> {
        val byApp = items.filter { it.kind == MemoryKind.Tip || it.kind == MemoryKind.Skill }.groupBy(::key)
        return byApp.map { (_, group) ->
            Group(nameOf(group), group.filter { it.kind == MemoryKind.Tip }, group.filter { it.kind == MemoryKind.Skill })
        }.sortedWith(compareByDescending<Group> { it.tips.size + it.routines.size }.thenBy { it.name.lowercase() })
    }

    /** The title without the app's name in front: "MyFitnessPal: check the diary first" → "Check the diary first". */
    public fun shortTitle(item: MemoryItem, app: String): String {
        val prefix = item.title.substringBefore(':', missingDelimiterValue = "")
        if (prefix.isEmpty() || normal(prefix) != normal(app)) return item.title
        return item.title.substringAfter(':').trim().replaceFirstChar { it.uppercase() }.ifEmpty { item.title }
    }

    /** The numbered lines of a routine ("1. Open Rapido") without their numbers. */
    public fun steps(text: String): List<String> =
        text.lines().mapNotNull { STEP.matchEntire(it.trim())?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty) }

    /** What a routine says besides its steps ("If it goes wrong: …"). */
    public fun notes(text: String): String =
        text.lines().filter { it.isNotBlank() && STEP.matchEntire(it.trim()) == null }.joinToString("\n") { it.trim() }

    private fun key(item: MemoryItem): String = normal(item.app?.takeIf { it.isNotBlank() } ?: item.subject)

    private fun nameOf(group: List<MemoryItem>): String {
        group.firstNotNullOfOrNull { it.app?.trim()?.takeIf(String::isNotEmpty) }?.let { return it }
        val key = key(group.first())
        group.firstNotNullOfOrNull { item -> item.title.substringBefore(':', "").trim().takeIf { it.isNotEmpty() && normal(it) == key } }
            ?.let { return it }
        return group.first().subject.trim().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    private fun normal(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private val STEP = Regex("""^\d{1,2}[.)]\s*(.+)$""")
}
