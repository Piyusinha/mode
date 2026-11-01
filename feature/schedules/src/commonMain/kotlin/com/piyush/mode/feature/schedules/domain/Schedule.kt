package com.piyush.mode.feature.schedules.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** When a task repeats: at [hour]:[minute] on [days], or once when [days] is empty. Local time. */
public data class ScheduleRule(val hour: Int, val minute: Int, val days: Set<DayOfWeek>) {
    init {
        require(hour in 0..23 && minute in 0..59) { "time must be 00:00 to 23:59" }
    }

    public val once: Boolean get() = days.isEmpty()

    /**
     * The first time after [afterMs] this rule is due, in epoch milliseconds.
     * When the clocks skip that time (spring forward) it runs at the first
     * moment that exists; when they repeat it (fall back), once.
     */
    @OptIn(ExperimentalTime::class)
    public fun nextRun(afterMs: Long, zone: TimeZone): Long {
        val today = Instant.fromEpochMilliseconds(afterMs).toLocalDateTime(zone).date
        for (offset in 0..7) {
            val date = today.plus(offset, DateTimeUnit.DAY)
            if (!once && date.dayOfWeek !in days) continue
            val at = date.atTime(hour, minute).toInstant(zone).toEpochMilliseconds()
            if (at > afterMs) return at
        }
        error("unreachable: every week has each day")
    }

    /** "08:00 on weekdays", "21:30 every day", "07:15 once". */
    public fun label(): String {
        val time = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        val when_ = when {
            once -> "once"
            days.size == 7 -> "every day"
            days == WEEKDAYS -> "on weekdays"
            days == WEEKEND -> "at weekends"
            else -> "on " + DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
        }
        return "$time $when_"
    }

    public companion object {
        public val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        public val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        public val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        /** Bit 0 = Monday … bit 6 = Sunday, as stored. */
        public fun daysFromBits(bits: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { bits and (1 shl (it.isoDayNumber - 1)) != 0 }.toSet()

        public fun bitsOf(days: Set<DayOfWeek>): Int = days.fold(0) { bits, d -> bits or (1 shl (d.isoDayNumber - 1)) }
    }
}

/** A task that repeats. */
public data class Schedule(
    val id: String,
    val text: String,
    val rule: ScheduleRule,
    val enabled: Boolean,
    /** Epoch milliseconds; null once a one-off has run or when switched off. */
    val nextRunAt: Long?,
    val lastRunAt: Long?,
)
