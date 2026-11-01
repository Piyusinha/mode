package com.piyush.mode.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class PlainTextTest {
    private val reply = """
        You have **4 things** today, see [your calendar](calshow://).

        - All day – Daylight Saving Time ends
        - 14:00 – *Biozyme* with lunch

        | Ride | Price |
        |---|---|
        | Ola Mini | ₹612 |
    """.trimIndent()

    @Test
    fun marksGoAndListsStayReadable() {
        assertEquals(
            "You have 4 things today, see your calendar.\n\n• All day – Daylight Saving Time ends\n• 14:00 – Biozyme with lunch\n\nRide · Price\nOla Mini · ₹612",
            PlainText.fromMarkdown(reply),
        )
    }

    @Test
    fun oneLineForStatusBars() {
        assertEquals(
            "You have 4 things today, see your calendar. • All day – Daylight Saving Time ends • 14:00 – Biozyme with lunch Ride · Price Ola Mini · ₹612",
            PlainText.fromMarkdown(reply, oneLine = true),
        )
    }

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("Dark mode is off. snake_case_name stays.", PlainText.fromMarkdown("Dark mode is off. snake_case_name stays."))
    }
}
