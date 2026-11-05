package com.piyush.mode.agent

import com.piyush.mode.agent.ScreenLayout.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenLayoutTest {
    private fun box(text: String, x: Int, y: Int, h: Int = 20) = Box(text, x, y, w = 80, h = h)

    // Two restaurant cards as Swiggy shows them, the second an ad, as OCR returns them (out of order).
    private val list = listOf(
        box("4.7 (106)", 80, 540), box("Matsuri", 120, 500), box("Japanese", 260, 540),
        box("AD", 40, 700), box("Shoyu", 130, 700), box("4.6 (2K+)", 80, 740),
    )

    @Test
    fun textOnOneLineIsOneRowInReadingOrder() {
        val l = ScreenLayout.of(listOf(box("Japanese", 260, 541), box("4.7", 80, 538)), 20)
        assertEquals(listOf("4.7", "Japanese"), l.cards.single().rows.single().map { it.text })
        assertEquals(listOf(1, 2), l.elements.map { it.id })
    }

    @Test
    fun aWideGapStartsANewCardAndAnAdIsMarked() {
        val l = ScreenLayout.of(list, 20)
        assertEquals(2, l.cards.size)
        assertEquals(listOf("Matsuri", "4.7 (106)", "Japanese"), l.cards[0].rows.flatten().map { it.text })
        assertFalse(l.cards[0].ad)
        assertTrue(l.cards[1].ad)
    }

    @Test
    fun theRenderedTextKeepsAnItemWithItsDetails() {
        val text = ScreenLayout.of(list, 20).render(80)
        assertEquals(
            """
            [1] "Matsuri" (120, 500)
            [2] "4.7 (106)" (80, 540)   [3] "Japanese" (260, 540)

            (ad) [4] "AD" (40, 700)   [5] "Shoyu" (130, 700)
            [6] "4.6 (2K+)" (80, 740)
            """.trimIndent() + "\n",
            text,
        )
    }

    @Test
    fun anAddButtonIsNotAnAd() {
        val l = ScreenLayout.of(listOf(box("Avocado Maki", 100, 300), box("ADD", 340, 300), box("Add item", 200, 330)), 20)
        assertFalse(l.cards.single().ad)
    }

    @Test
    fun beyondTheLimitTheRestIsCounted() {
        val l = ScreenLayout.of(List(10) { box("row $it", 100, 100 + it * 30) }, 20)
        val text = l.render(4)
        assertTrue("[4] \"row 3\"" in text)
        assertFalse("[5]" in text)
        assertTrue("(6 more not listed)" in text, text)
        assertEquals(370, l.byId(10)?.y)
    }

    @Test
    fun withoutSizesTheLineHeightStandsIn() {
        val l = ScreenLayout.of(listOf(Box("Title", 100, 300), Box("Detail", 100, 325), Box("Next", 100, 420)), 22)
        assertEquals(2, l.cards.size)
    }

    @Test
    fun aFormRowHasAValueButAMailRowDoesNot() {
        // Mail search results, 3 Nov 19:44: the hint sent the agent to tap "9/26/26 >".
        val mail = ScreenLayout.of(listOf(
            box("FlightsMojo India", 120, 300), box("9/26/26 >", 400, 300), box("...Your booking is confirmed", 250, 320),
            box("LinkedIn", 135, 351), box("Friday >", 410, 355),
        ), 20)
        assertEquals(null, mail.valueOf("FlightsMojo India"))
        assertEquals(null, mail.valueOf("LinkedIn"))
        // MyFitnessPal's serving form: the value sits to the right of its label.
        val form = ScreenLayout.of(listOf(
            box("Serving Size", 100, 300), box("6 pieces", 380, 300),
            box("Number of Servings", 100, 340), box("1", 380, 340),
            box("Meal", 100, 380), box("Dinner", 380, 380),
        ), 20)
        assertEquals("6 pieces", form.valueOf("Serving Size")?.text)
        assertEquals("1", form.valueOf("Number of Servings")?.text)
        assertEquals("Dinner", form.valueOf("Meal")?.text)
        // A settings row with "On >" is a list row too; a time is not a value.
        val settings = ScreenLayout.of(listOf(box("Wi-Fi", 100, 300), box("On >", 380, 300), box("Alarm", 100, 340), box("6:30 AM", 380, 340)), 20)
        assertEquals(null, settings.valueOf("Wi-Fi"))
        assertEquals(null, settings.valueOf("Alarm"))
    }
}
