package com.piyush.mode.app.screens.common

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** When something happened, relative to today; the native UI words it. */
public sealed interface RelativeDay {
    public data object Today : RelativeDay
    public data object Yesterday : RelativeDay
    /** Within the last week: 1 = Monday … 7 = Sunday. */
    public data class Weekday(val isoDay: Int) : RelativeDay
    public data class Date(val epochMs: Long) : RelativeDay
}

public enum class PartOfDay { Morning, Afternoon, Evening, Night }

/** The clock and time zone screens use, replaceable in tests. */
@OptIn(ExperimentalTime::class)
public class ScreenClock(
    public val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    public val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    public fun relativeDay(epochMs: Long): RelativeDay {
        val z = zone()
        val today = Instant.fromEpochMilliseconds(now()).toLocalDateTime(z).date
        val then = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(z).date
        return when (val days = then.daysUntil(today)) {
            0 -> RelativeDay.Today
            1 -> RelativeDay.Yesterday
            in 2..6 -> RelativeDay.Weekday(then.dayOfWeek.isoDayNumber)
            else -> if (days < 0) RelativeDay.Today else RelativeDay.Date(epochMs)
        }
    }

    public fun partOfDay(): PartOfDay = when (Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone()).hour) {
        in 5..11 -> PartOfDay.Morning
        in 12..16 -> PartOfDay.Afternoon
        in 17..21 -> PartOfDay.Evening
        else -> PartOfDay.Night
    }
}
