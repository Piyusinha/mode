package com.piyush.mode.feature.tasks

import com.piyush.mode.feature.tasks.domain.Routines
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutinesTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime(2026, 11, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    @Test
    fun aTaskDoneMostWeekdaysAtTheSameHourIsARoutineAndIsDueAroundThen() {
        // Mon 2, Tue 3, Wed 4 November 2026 around 8:40; Sat 7 at 11:00 is a different kind of day.
        val tasks = listOf(
            Routines.Candidate("Book a cab to work on Rapido", at(2, 8, 38), true),
            Routines.Candidate("book cab to work rapido", at(3, 8, 45), true),
            Routines.Candidate("Can you book a cab to work on Rapido", at(4, 8, 35), true),
            Routines.Candidate("Book a cab to work on Rapido", at(7, 11, 0), true),
            Routines.Candidate("Turn off dark mode", at(2, 22, 0), true),
            Routines.Candidate("Turn off dark mode", at(3, 22, 5), false),
        )
        val routines = Routines.detect(tasks, zone)
        assertEquals(1, routines.size, routines.toString())
        val cab = routines.single()
        assertTrue(cab.weekday); assertEquals(8, cab.hour); assertEquals(38, cab.minute); assertEquals(3, cab.times)
        assertEquals(cab, Routines.due(routines, tasks, at(5, 8, 20), zone), "Thu 5 Nov 8:20: about due")
        assertNull(Routines.due(routines, tasks, at(5, 13, 0), zone), "not at lunch")
        assertNull(Routines.due(routines, tasks, at(8, 8, 40), zone), "not on a Sunday")
        assertNull(Routines.due(routines, tasks + Routines.Candidate("book cab to work", at(5, 8, 10), true), at(5, 8, 20), zone), "already done today")
    }

    @Test
    fun twoTimesAreNotARoutine() {
        val tasks = listOf(Routines.Candidate("Order oats", at(2, 9, 0), true), Routines.Candidate("Order oats", at(3, 9, 0), true))
        assertEquals(emptyList(), Routines.detect(tasks, zone))
    }
}
