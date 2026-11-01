package com.piyush.mode.feature.schedules

import com.piyush.mode.feature.schedules.domain.ScheduleRule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduleRuleTest {
    private val india = TimeZone.of("Asia/Kolkata")
    private val newYork = TimeZone.of("America/New_York")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: TimeZone = india) =
        LocalDateTime(y, mo, d, h, mi).toInstant(zone).toEpochMilliseconds()

    @Test
    fun dailyRunsLaterTodayOrTomorrow() {
        val rule = ScheduleRule(8, 0, ScheduleRule.EVERY_DAY)
        assertEquals(at(2026, 11, 1, 8, 0), rule.nextRun(at(2026, 11, 1, 7, 30), india))
        assertEquals(at(2026, 11, 2, 8, 0), rule.nextRun(at(2026, 11, 1, 8, 0), india), "exactly on time counts as passed")
        assertEquals(at(2026, 11, 2, 8, 0), rule.nextRun(at(2026, 11, 1, 21, 0), india))
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val rule = ScheduleRule(9, 15, ScheduleRule.WEEKDAYS)
        // 1 Nov 2026 is a Sunday; the next weekday is Monday 2 Nov.
        assertEquals(at(2026, 11, 2, 9, 15), rule.nextRun(at(2026, 11, 1, 6, 0), india))
        // Friday evening → Monday.
        assertEquals(at(2026, 11, 9, 9, 15), rule.nextRun(at(2026, 11, 6, 18, 0), india))
    }

    @Test
    fun onceIsTheNextTimeThatClockTimeComesRound() {
        val rule = ScheduleRule(7, 15, emptySet())
        assertEquals(at(2026, 11, 2, 7, 15), rule.nextRun(at(2026, 11, 1, 22, 0), india))
    }

    @Test
    fun aTimeTheClocksSkipRunsAtTheFirstMomentThatExists() {
        val rule = ScheduleRule(2, 30, ScheduleRule.EVERY_DAY)
        // 8 Mar 2026, New York: 02:00 jumps to 03:00.
        val run = rule.nextRun(at(2026, 3, 8, 0, 0, newYork), newYork)
        assertEquals(at(2026, 3, 8, 3, 30, newYork), run)
    }

    @Test
    fun aTimeTheClocksRepeatRunsOnce() {
        val rule = ScheduleRule(1, 30, ScheduleRule.EVERY_DAY)
        // 1 Nov 2026, New York: 01:00–02:00 happens twice.
        val first = rule.nextRun(at(2026, 11, 1, 0, 0, newYork), newYork)
        val second = rule.nextRun(first, newYork)
        assertEquals(at(2026, 11, 2, 1, 30, newYork), second, "not again an hour later")
    }

    @Test
    fun daysRoundTripThroughTheStoredBits() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(days, ScheduleRule.daysFromBits(ScheduleRule.bitsOf(days)))
        assertEquals(0b0011111, ScheduleRule.bitsOf(ScheduleRule.WEEKDAYS))
    }

    @Test
    fun labelsReadNaturally() {
        assertEquals("08:00 on weekdays", ScheduleRule(8, 0, ScheduleRule.WEEKDAYS).label())
        assertEquals("21:30 every day", ScheduleRule(21, 30, ScheduleRule.EVERY_DAY).label())
        assertEquals("07:05 once", ScheduleRule(7, 5, emptySet()).label())
        assertEquals("10:00 on Mon, Thu", ScheduleRule(10, 0, setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)).label())
        assertFailsWith<IllegalArgumentException> { ScheduleRule(24, 0, emptySet()) }
    }
}
