package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.conversation.ReplyPart
import com.piyush.mode.app.screens.conversation.TimeRow
import com.piyush.mode.app.screens.conversation.replyParts
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyMarkdownTest {
    @Test
    fun aCalendarReplyBecomesAnAgenda() {
        val reply = """
            You have **4 things** today, Sunday 1 November.

            - All day – Daylight Saving Time ends
            - 14:00 – Biozyme with lunch
            - 21:30–22:00 – Shelcal + Fish Oil + Magnesium
            - **23:00** Softovac before bed
        """.trimIndent()
        assertEquals(
            listOf(
                ReplyPart.Paragraph("You have **4 things** today, Sunday 1 November."),
                ReplyPart.Agenda(
                    listOf(
                        TimeRow(null, null, "Daylight Saving Time ends"),
                        TimeRow("14:00", null, "Biozyme with lunch"),
                        TimeRow("21:30", "22:00", "Shelcal + Fish Oil + Magnesium"),
                        TimeRow("23:00", null, "Softovac before bed"),
                    ),
                ),
            ),
            replyParts(reply),
        )
    }

    @Test
    fun markdownBlocksAreKeptApart() {
        val reply = """
            ### Cheapest flights
            Three options for **Friday**:

            | Airline | Time | Price |
            |---|:--:|---:|
            | IndiGo | 06:10 | ₹4,200 |
            | Air India | 09:45 |

            1. Open the app
            2. Tap **Book**
              then confirm

            > Prices change often.

            ---
            ```
            PNR AB12CD
            ```
            - Window seat
            - No meal
        """.trimIndent()
        assertEquals(
            listOf(
                ReplyPart.Heading(3, "Cheapest flights"),
                ReplyPart.Paragraph("Three options for **Friday**:"),
                ReplyPart.Table(listOf("Airline", "Time", "Price"), listOf(listOf("IndiGo", "06:10", "₹4,200"), listOf("Air India", "09:45", ""))),
                ReplyPart.Numbered(1, listOf("Open the app", "Tap **Book** then confirm")),
                ReplyPart.Quote("Prices change often."),
                ReplyPart.Divider,
                ReplyPart.Code("PNR AB12CD"),
                ReplyPart.Bullets(listOf("Window seat", "No meal")),
            ),
            replyParts(reply),
        )
    }

    @Test
    fun plainTextStaysOneParagraph() {
        assertEquals(listOf(ReplyPart.Paragraph("Dark mode is off.\nAppearance is Light.")), replyParts("Dark mode is off.\nAppearance is Light."))
    }
}
