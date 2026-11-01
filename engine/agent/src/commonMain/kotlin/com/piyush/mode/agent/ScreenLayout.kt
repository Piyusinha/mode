package com.piyush.mode.agent

import kotlin.math.abs

/**
 * The screen's text as the model reads it: numbered elements, in reading order, grouped into rows
 * and cards. Seen on the phone: a flat list of OCR fragments ("Matsuri", "4.7", "(106)", "AD",
 * "Shoyu", "4.6") hid which rating and which AD belonged to which restaurant, and the agent
 * scrolled up and down to re-check. Grouping by position keeps an item with its details, and the
 * numbers make taps exact ("tap id 12"), whatever the model's coordinate habits.
 *
 * Pure geometry: nothing here knows any app.
 */
class ScreenLayout private constructor(val cards: List<Card>) {

    /** One piece of recognised text: its centre and size in screenshot pixels (size 0 when unknown). */
    class Box(val text: String, val x: Int, val y: Int, val w: Int = 0, val h: Int = 0)

    class Element(val id: Int, val text: String, val x: Int, val y: Int)

    /** Rows that belong together (an item and its details); [ad] when it is marked as an ad. */
    class Card(val rows: List<List<Element>>, val ad: Boolean)

    val elements: List<Element> = cards.flatMap { it.rows.flatten() }

    fun byId(id: Int): Element? = elements.firstOrNull { it.id == id }

    /** One line per row, a blank line between cards, "(ad)" before an ad's first row; at most [max] elements. */
    fun render(max: Int): String = buildString {
        var shown = 0
        for ((c, card) in cards.withIndex()) {
            if (shown >= max) break
            if (c > 0) append('\n')
            for ((r, row) in card.rows.withIndex()) {
                if (shown >= max) break
                if (r == 0 && card.ad) append("(ad) ")
                row.take(max - shown).forEachIndexed { i, e ->
                    if (i > 0) append("   ")
                    append('[').append(e.id).append("] \"").append(e.text).append("\" (").append(e.x).append(", ").append(e.y).append(')')
                    shown++
                }
                append('\n')
            }
        }
        val hidden = elements.size - shown
        if (hidden > 0) append("(").append(hidden).append(" more not listed)\n")
    }

    companion object {
        /** A card ends where the gap to the next row is over this many line heights. */
        const val CARD_GAP = 1.6

        private val AD = Regex("""^(ad|ads|sponsored|promoted)\b""", RegexOption.IGNORE_CASE)

        /** Groups [boxes]; [lineHeight] stands in for a box's height when it is unknown. */
        fun of(boxes: List<Box>, lineHeight: Int): ScreenLayout {
            fun h(b: Box) = if (b.h > 0) b.h else lineHeight
            val sorted = boxes.filter { it.text.isNotBlank() }.sortedWith(compareBy<Box> { it.y }.thenBy { it.x })

            // Rows: boxes whose centres sit within half a line of the row's first box.
            val rows = mutableListOf<MutableList<Box>>()
            for (b in sorted) {
                val row = rows.lastOrNull()
                val first = row?.first()
                if (first != null && abs(b.y - first.y) <= maxOf(h(b), h(first)) / 2) row += b else rows += mutableListOf(b)
            }
            rows.forEach { it.sortBy(Box::x) }

            // Cards: a new one where the gap between rows is wider than usual line spacing.
            val median = rows.flatten().map(::h).sorted().let { if (it.isEmpty()) lineHeight else it[it.size / 2] }
            val groups = mutableListOf<MutableList<List<Box>>>()
            var lastBottom: Int? = null
            for (row in rows) {
                val top = row.minOf { it.y - h(it) / 2 }
                val bottom = row.maxOf { it.y + h(it) / 2 }
                if (lastBottom == null || top - lastBottom > CARD_GAP * median) groups += mutableListOf<List<Box>>()
                groups.last() += row
                lastBottom = bottom
            }

            var next = 1
            val cards = groups.map { group ->
                val rowsOut = group.map { row -> row.map { Element(next++, it.text.trim(), it.x, it.y) } }
                Card(rowsOut, ad = rowsOut.flatten().any { AD.containsMatchIn(it.text.trim()) })
            }
            return ScreenLayout(cards)
        }

        val Empty = ScreenLayout(emptyList())
    }
}
