package com.piyush.mode.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModeInlineMarkdownTest {
    private fun parse(s: String) = ModeInlineMarkdown.parse(s, Color.Blue)

    @Test
    fun boldItalicAndCodeLoseTheirMarks() {
        val a = parse("Your **3 pm** call is *moved* to `Room 4`")
        assertEquals("Your 3 pm call is moved to Room 4", a.text)
        assertTrue(a.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold && a.text.substring(it.start, it.end) == "3 pm" })
        assertTrue(a.spanStyles.any { it.item.fontStyle == FontStyle.Italic && a.text.substring(it.start, it.end) == "moved" })
    }

    @Test
    fun linksKeepTheirWordsAndTarget() {
        val a = parse("See [the order](https://example.com/o/1).")
        assertEquals("See the order.", a.text)
        val link = a.getLinkAnnotations(0, a.length).single()
        assertEquals("https://example.com/o/1", (link.item as LinkAnnotation.Url).url)
    }

    @Test
    fun unfinishedMarksAndSnakeCaseStayAsWritten() {
        assertEquals("2 * 3 = 6 and **open", parse("2 * 3 = 6 and **open").text)
        assertEquals("file_name_here", parse("file_name_here").text)
        assertEquals("a [b] c", parse("a [b] c").text)
    }
}
