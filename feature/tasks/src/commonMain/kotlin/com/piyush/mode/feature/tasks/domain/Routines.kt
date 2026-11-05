package com.piyush.mode.feature.tasks.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What the user does at the same time most days ("a cab to work, weekdays around 8:40"), found in their past
 * tasks: the same words, done, on the same kind of day (weekday or weekend) at about the same hour, at least
 * [MIN_TIMES] times. Mode only suggests these, never runs them unasked; after two dismissals it stops.
 * Research: proactive suggestions are wrong often (PersonalAlign: half the suggestions were false alarms
 * until time and scenario were required), so stability in both is required here.
 */
public object Routines {
    public data class Routine(val text: String, val weekday: Boolean, val hour: Int, val minute: Int, val times: Int, val key: String)

    public data class Candidate(val text: String, val atMs: Long, val done: Boolean)

    public fun detect(tasks: List<Candidate>, zone: TimeZone, nowMs: Long = 0L): List<Routine> {
        // Phrasings of the same task vary ("book a cab to work on Rapido", "cab to work"): grouped by word overlap.
        val groups = mutableListOf<Pair<String, MutableList<Candidate>>>()
        for (t in tasks.filter { it.done }) {
            val k = key(t.text)
            val group = groups.firstOrNull { (gk, _) -> similar(gk, k) }
            if (group != null) group.second += t else groups += k to mutableListOf(t)
        }
        return groups.mapNotNull { (key, members) ->
            if (members.size < MIN_TIMES) return@mapNotNull null
            val local = members.map { Instant.fromEpochMilliseconds(it.atMs).toLocalDateTime(zone) }
            val weekday = local.count { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            val onWeekdays = weekday * 2 >= local.size
            val same = local.filter { (it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY) == onWeekdays }
            if (same.size < MIN_TIMES) return@mapNotNull null
            val minutes = same.map { it.hour * 60 + it.minute }.sorted()
            val median = minutes[minutes.size / 2]
            if (minutes.count { kotlin.math.abs(it - median) <= WINDOW_MINUTES } < MIN_TIMES) return@mapNotNull null
            val newest = members.maxBy { it.atMs }
            Routine(newest.text, onWeekdays, median / 60, median % 60, same.size, key)
        }
    }

    /** The routine due about now (within [WINDOW_MINUTES] before or after), if any, and not done today. */
    public fun due(routines: List<Routine>, tasks: List<Candidate>, nowMs: Long, zone: TimeZone): Routine? {
        val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone)
        val weekdayNow = now.dayOfWeek != DayOfWeek.SATURDAY && now.dayOfWeek != DayOfWeek.SUNDAY
        val minutesNow = now.hour * 60 + now.minute
        return routines.firstOrNull { r ->
            r.weekday == weekdayNow && kotlin.math.abs(minutesNow - (r.hour * 60 + r.minute)) <= WINDOW_MINUTES &&
                tasks.none { t -> similar(key(t.text), r.key) && Instant.fromEpochMilliseconds(t.atMs).toLocalDateTime(zone).date == now.date }
        }
    }

    /** The task's words that matter, sorted, as the routine's key: "book cab home work" for any phrasing of it. */
    public fun key(text: String): String = WORD.findAll(text.lowercase()).map { it.value }.filter { it !in STOP }.toSet().sorted().joinToString(" ")

    /** Two keys name the same task when their words overlap this much (Jaccard). */
    public fun similar(a: String, b: String): Boolean {
        val x = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val y = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (x.isEmpty() || y.isEmpty()) return false
        return (x intersect y).size.toDouble() / (x union y).size >= SIMILAR
    }

    public const val SIMILAR: Double = 0.6
    public const val MIN_TIMES: Int = 3
    public const val WINDOW_MINUTES: Int = 45
    private val WORD = Regex("""[\p{L}\p{N}]{3,}""")
    private val STOP = setOf("the", "and", "for", "can", "you", "please", "with", "from", "that", "this", "one", "thing", "hey", "mode", "now", "today", "tomorrow", "just", "also", "then", "need", "want", "would", "like", "could", "some", "its", "get")
}
