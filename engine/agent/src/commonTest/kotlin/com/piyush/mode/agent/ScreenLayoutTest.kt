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
}
