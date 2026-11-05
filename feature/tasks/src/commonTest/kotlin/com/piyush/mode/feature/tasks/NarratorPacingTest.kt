package com.piyush.mode.feature.tasks

import com.piyush.mode.feature.tasks.presentation.Narrator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NarratorPacingTest {
    @Test
    fun aFiveMinuteTaskIsKeptCompanyThroughout() {
        // 5 Nov: one update at 8 s, then five and a half minutes of silence. Progress now moves with the steps
        // (TaskRunner: "<plan item>|<steps / 2>"), about a step every ten seconds.
        val pacing = Narrator.Pacing()
        var spoken = 0
        for (second in 0..330) {
            val elapsed = second * 1000L
            val steps = second / 10
            val progress = "${(steps / 9).coerceAtMost(2)}|${steps / 2}"
            if (pacing.due(elapsed, progress)) { pacing.said(elapsed, progress); spoken++ }
        }
        assertTrue(spoken in 12..17, "about one line every 20–25 s over 5.5 minutes, got $spoken")
    }

    @Test
    fun nothingNewMeansNothingSaid() {
        val pacing = Narrator.Pacing()
        assertFalse(pacing.due(5_000, "0|1"), "not in the first eight seconds")
        assertTrue(pacing.due(9_000, "0|1")); pacing.said(9_000, "0|1")
        assertFalse(pacing.due(40_000, "0|1"), "the same progress is not said twice")
        assertFalse(pacing.due(20_000, "0|2"), "not within 20 s of the last line")
        assertTrue(pacing.due(30_000, "0|2"))
        assertEquals(20, Narrator.MAX_UPDATES)
    }
}
