package com.piyush.mode.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * The inline part of Markdown a reply uses: **bold**, *italic* or _italic_, `code`, ~~struck~~ and
 * [links](https://…). Anything it doesn't understand stays as written (like the iPhone's partial parse).
 */
public object ModeInlineMarkdown {
    public fun parse(text: String, link: Color): AnnotatedString = buildAnnotatedString { append(text, 0, text.length, link) }

    private fun AnnotatedString.Builder.append(s: String, from: Int, to: Int, link: Color) {
        var i = from
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                append(plain.toString())
                plain.clear()
            }
        }
        while (i < to) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < to -> {
                    plain.append(s[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val end = s.indexOf('`', i + 1).takeIf { it in (i + 1) until to }
                    if (end == null) { plain.append(c); i++ } else {
                        flush()
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(s.substring(i + 1, end)) }
                        i = end + 1
                    }
                }
                s.startsWith("**", i) || s.startsWith("__", i) -> {
                    val mark = s.substring(i, i + 2)
                    val end = s.indexOf(mark, i + 2).takeIf { it in (i + 2) until to }
                    if (end == null || end == i + 2) { plain.append(mark); i += 2 } else {
                        flush()
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s, i + 2, end, link) }
                        i = end + 2
                    }
                }
                s.startsWith("~~", i) -> {
                    val end = s.indexOf("~~", i + 2).takeIf { it in (i + 2) until to }
                    if (end == null) { plain.append("~~"); i += 2 } else {
                        flush()
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(s, i + 2, end, link) }
                        i = end + 2
                    }
                }
                (c == '*' || c == '_') && i + 1 < to && !s[i + 1].isWhitespace() && opensItalic(s, i) -> {
                    val end = closing(s, c, i + 1, to)
                    if (end == null) { plain.append(c); i++ } else {
                        flush()
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s, i + 1, end, link) }
                        i = end + 1
                    }
                }
                c == '[' -> {
                    val close = s.indexOf(']', i + 1).takeIf { it in (i + 1) until to }
                    val url = close?.let { if (it + 1 < to && s[it + 1] == '(') s.indexOf(')', it + 2).takeIf { e -> e in (it + 2) until to } else null }
                    if (close == null || url == null) { plain.append(c); i++ } else {
                        flush()
                        val target = s.substring(close + 2, url)
                        withLink(LinkAnnotation.Url(target, TextLinkStyles(SpanStyle(color = link)))) { append(s, i + 1, close, link) }
                        i = url + 1
                    }
                }
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flush()
    }

    /** `_` inside a word (snake_case) is not italic; `*` is. */
    private fun opensItalic(s: String, i: Int): Boolean = s[i] == '*' || i == 0 || !s[i - 1].isLetterOrDigit()

    private fun closing(s: String, mark: Char, from: Int, to: Int): Int? {
        var j = from
        while (j < to) {
            if (s[j] == mark && !s[j - 1].isWhitespace() && (mark == '*' || j + 1 >= to || !s[j + 1].isLetterOrDigit())) {
                if (!(mark == '*' && j + 1 < to && s[j + 1] == '*')) return j
            }
            j++
        }
        return null
    }
}
