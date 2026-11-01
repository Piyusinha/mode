package com.piyush.mode.feature.memory.data

import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind
import kotlin.math.exp
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The "understand" step before a task (docs/harness.md §3): picks what the
 * agent should know, with no model call.
 *
 * - **About you:** every fact, like ChatGPT's saved memories, up to a budget;
 *   when there are more, the ones that match the task and are used most.
 * - **Skills:** scored on their keywords, title and app against the task
 *   (EvoSkill-GUI's metadata matching). The best one above [SKILL_THRESHOLD]
 *   is given in full; the next few by name only, for `recall`.
 * - **Tips:** for apps the task names, or that a matched fact or skill names
 *   (one hop: "Sonali: WhatsApp" brings WhatsApp's tips).
 *
 * Ties go to items that worked recently and often (MAGNET's retention:
 * exp(−days since last use ÷ uses)).
 */
@OptIn(ExperimentalTime::class)
public class MemoryRetrieval(
    private val memory: MemoryRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    public class ForTask(
        public val aboutYou: List<String>,
        /** The best skill in full first (if any matched well), then names of other close ones. */
        public val skills: List<String>,
        public val tips: List<String>,
        /** Every item shown, to record how the task went with them. */
        public val usedIds: List<String>,
        /**
         * The steps of a skill that fits the task closely and has worked: the agent can follow it without
         * asking the planning brain first (that call is most of a task's cost). Null otherwise.
         */
        public val knownRoute: String? = null,
    )

    public fun forTask(task: String): ForTask {
        val items = memory.all()
        val words = Words.of(task)
        val facts = items.filter { it.kind == MemoryKind.Fact }
        // A task that names its app ("on Swiggy") gets no skills from other apps: seen on the phone,
        // a Zomato skill offered for a Swiggy task sent the agent looking it up three times.
        val taskApp = APP_IN_TASK.findAll(task).map { Words.normal(it.groupValues[2]) }.toList()
        fun otherApp(item: MemoryItem): Boolean {
            val app = item.app?.let(Words::normal)?.takeIf { it.isNotEmpty() } ?: return false
            return taskApp.isNotEmpty() && taskApp.none { it.contains(app) || app.contains(it) } && !appNamed(app, words)
        }
        val skills = items.filter { it.kind == MemoryKind.Skill && !otherApp(it) }
        val tips = items.filter { it.kind == MemoryKind.Tip }
        val people = facts.map { it.subject }.toSet()

        val shownFacts = if (facts.size <= MAX_FACTS) facts.sortedByDescending { retention(it) }
        else facts.sortedWith(compareByDescending<MemoryItem> { factScore(it, words) }.thenByDescending { retention(it) }).take(MAX_FACTS)

        val apps = (tips.map { it.subject } + skills.mapNotNull { it.app?.lowercase() }).toSet()
        // Words the task brings with it through what is known: "message Sonali" + "Sonali: WhatsApp".
        val matchedFacts = shownFacts.filter { factScore(it, words) > 0 }
        val viaFacts = Words.of(matchedFacts.joinToString(" ") { it.text })
        val ranked = skills.map { it to skillScore(it, words, people, viaFacts) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryItem, Double>> { it.second }.thenByDescending { retention(it.first) })
        val best = ranked.firstOrNull()?.takeIf { it.second >= SKILL_THRESHOLD }?.first
        val knownRoute = ranked.firstOrNull()
            ?.takeIf { (s, score) -> score >= KNOWN_ROUTE && s.successes >= 1 && s.successes * 2 >= s.uses }
            ?.first?.text
        val named = ranked.filter { it.first != best }.take(MAX_SKILL_NAMES).map { it.first }

        // Apps the task names, plus those a matched fact or the best skill points to.
        val hop = viaFacts + Words.of(listOfNotNull(best?.app, best?.text).joinToString(" "))
        val wanted = apps.filter { app -> appNamed(app, words) || appNamed(app, hop) }.toSet()
        val shownTips = tips.filter { it.subject in wanted }.sortedByDescending { retention(it) }.take(MAX_TIPS)

        return ForTask(
            aboutYou = shownFacts.map { it.text },
            skills = listOfNotNull(best?.let(::skillInFull)) +
                named.map { "Also known${it.app?.let { a -> " (in $a)" }.orEmpty()}: ${it.title} (recall \"${it.title}\" for the steps)" },
            tips = shownTips.map { "${it.title}: ${it.text}" },
            usedIds = (shownFacts + listOfNotNull(best) + shownTips).map { it.id },
            knownRoute = knownRoute,
        )
    }

    /** Lines for a lookup in the middle of a task: the best few matches of any kind. */
    public fun recall(query: String, limit: Int = 5, apps: List<String> = emptyList()): List<String> {
        val words = Words.of(query)
        if (words.isEmpty()) return emptyList()
        // Seen on the phone: "order sushi on Zomato" brought back a Swish skill on common words.
        // With apps given, what belongs to another app is left out; people and preferences stay.
        val opened = apps.map(Words::normal).filter { it.isNotEmpty() }
        fun forTheseApps(item: MemoryItem): Boolean {
            val app = item.app?.let(Words::normal)?.takeIf { it.isNotEmpty() } ?: return true
            return opened.isEmpty() || opened.any { it.contains(app) || app.contains(it) }
        }
        return memory.all().filter(::forTheseApps)
            .map {
                val exactTitle = if (Words.normal(it.title) == Words.normal(query)) 100 else 0
                it to exactTitle + (Words.of("${it.subject} ${it.title} ${it.text} ${it.keywords.joinToString(" ")} ${it.app.orEmpty()}") intersect words).size
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryItem, Int>> { it.second }.thenByDescending { retention(it.first) })
            .take(limit)
            .map { (item, _) -> if (item.kind == MemoryKind.Skill) skillInFull(item) else "${item.title}: ${item.text}" }
    }

    /** An app's tips, for when the agent opens it. */
    public fun tipsFor(app: String): List<String> {
        val words = Words.of(app)
        return memory.ofKind(MemoryKind.Tip)
            .filter { appNamed(it.subject, words) || Words.of(it.subject) == words }
            .sortedByDescending { retention(it) }
            .take(MAX_TIPS)
            .map { "${it.title}: ${it.text}" }
    }

    private fun skillInFull(s: MemoryItem): String = buildString {
        append("SKILL: ").append(s.title)
        if (s.uses > 0) append(" (worked ").append(s.successes).append(" of ").append(s.uses).append(" times)")
        append('\n').append(s.text)
    }

    private fun factScore(f: MemoryItem, words: Set<String>): Int = (Words.of("${f.subject} ${f.text}") intersect words).size

    /**
     * 0.5 × how much of the task the skill covers + 0.3 × how much of the skill
     * the task covers + 0.2 when the task names the skill's app, directly or
     * through a fact it matched ([viaFacts]). Names of
     * people the user knows don't count against a skill: "message Sonali" fits
     * "Message [Person] on WhatsApp".
     */
    internal fun skillScore(s: MemoryItem, task: Set<String>, people: Set<String>, viaFacts: Set<String>): Double {
        val keys = Words.of("${s.title} ${s.keywords.joinToString(" ")}")
        val asked = task - people.flatMap { Words.of(it) }.toSet()
        val matched = (asked intersect keys).size
        if (matched == 0 || asked.isEmpty() || keys.isEmpty()) return 0.0
        val appBonus = if (s.app != null && (appNamed(s.app, task) || appNamed(s.app, viaFacts))) 0.2 else 0.0
        return 0.5 * matched / asked.size + 0.3 * matched / keys.size + appBonus
    }

    private fun appNamed(app: String, words: Set<String>): Boolean {
        val appWords = Words.of(app)
        return appWords.isNotEmpty() && words.containsAll(appWords)
    }

    private fun retention(item: MemoryItem): Double {
        val days = (now() - (item.lastUsedAt ?: item.updatedAt)).coerceAtLeast(0) / 86_400_000.0
        return exp(-days / max(item.successes, 1))
    }

    public companion object {
        public const val MAX_FACTS: Int = 25
        public const val MAX_TIPS: Int = 10
        public const val MAX_SKILL_NAMES: Int = 3
        public const val SKILL_THRESHOLD: Double = 0.5
        /** A skill this close to the task, that has worked at least half the times it was used, needs no brief. */
        public const val KNOWN_ROUTE: Double = 0.6

        /** "on Swiggy", "in MyFitnessPal", "using Uber": a capitalised name after a little word. */
        private val APP_IN_TASK = Regex("""\b(on|in|from|using|via|with|open)\s+([A-Z][\w&'-]{2,})""")
    }
}
