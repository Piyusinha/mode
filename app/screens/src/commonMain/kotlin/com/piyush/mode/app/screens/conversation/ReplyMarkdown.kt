package com.piyush.mode.app.screens.conversation

/**
 * One block of Mode's reply. The agent writes Markdown; the app draws each
 * block natively. Inline marks (bold, italic, `code`, links) stay in the text
 * for the view's text style to render.
 */
public sealed interface ReplyPart {
    public data class Paragraph(val text: String) : ReplyPart
    public data class Heading(val level: Int, val text: String) : ReplyPart
    public data class Bullets(val items: List<String>) : ReplyPart
    public data class Numbered(val start: Int, val items: List<String>) : ReplyPart

    /** Timed items (calendar events, reminders), drawn as an agenda. */
    public data class Agenda(val rows: List<TimeRow>) : ReplyPart
    public data class Table(val header: List<String>, val rows: List<List<String>>) : ReplyPart
    public data class Quote(val text: String) : ReplyPart
    public data class Code(val text: String) : ReplyPart
    public data object Divider : ReplyPart
}

/** "14:00–15:00 Biozyme with lunch"; [start] is null for an all-day item. */
public data class TimeRow(val start: String?, val end: String?, val text: String)

private val HEADING = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
private val DIVIDER = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val BULLET = Regex("""^\s*[-*•+]\s+(.+)$""")
private val NUMBERED = Regex("""^\s*(\d{1,3})[.)]\s+(.+)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val TABLE_RULE = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
private const val CLOCK = """\d{1,2}[:.]\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?"""
private val TIMED = Regex(
    """^(?:\*\*)?(?:(all[- ]day)|($CLOCK)(?:\s*[-–—]\s*($CLOCK))?)(?:\*\*)?\s*(?:[-–—·:|]\s*)?(.+)$""",
    RegexOption.IGNORE_CASE,
)

private fun timeRow(text: String): TimeRow? {
    val m = TIMED.find(text.trim()) ?: return null
    val (allDay, start, end, rest) = m.destructured
    if (rest.isBlank()) return null
    fun clock(s: String) = s.trim().replace(Regex("""^(\d{1,2})\.(\d{2})"""), "$1:$2").ifBlank { null }
    return TimeRow(if (allDay.isNotEmpty()) null else clock(start), clock(end), rest.trim())
}

private fun cells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

/** Splits Mode's Markdown reply into blocks; plain text becomes paragraphs. */
public fun replyParts(text: String): List<ReplyPart> {
    val lines = text.replace("\r\n", "\n").lines()
    val parts = mutableListOf<ReplyPart>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isEmpty()) return
        // A paragraph made only of timed lines (the older reply style) is an agenda too.
        val rows = paragraph.map(::timeRow)
        parts += if (rows.all { it != null }) ReplyPart.Agenda(rows.filterNotNull()) else ReplyPart.Paragraph(paragraph.joinToString("\n"))
        paragraph.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) code += lines[i++]
                parts += ReplyPart.Code(code.joinToString("\n").trimEnd())
            }
            trimmed.isEmpty() -> flush()
            HEADING.matches(trimmed) -> {
                flush()
                val m = HEADING.find(trimmed)!!
                parts += ReplyPart.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            DIVIDER.matches(trimmed) -> {
                flush()
                parts += ReplyPart.Divider
            }
            trimmed.startsWith("|") && i + 1 < lines.size && TABLE_RULE.matches(lines[i + 1]) -> {
                flush()
                val header = cells(trimmed)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += cells(lines[i]).let { r -> List(header.size) { r.getOrElse(it) { "" } } }
                    i++
                }
                parts += ReplyPart.Table(header, rows)
                continue
            }
            QUOTE.matches(line) -> {
                flush()
                val quoted = mutableListOf<String>()
                while (i < lines.size && QUOTE.matches(lines[i])) {
                    quoted += QUOTE.find(lines[i])!!.groupValues[1]
                    i++
                }
                parts += ReplyPart.Quote(quoted.joinToString("\n").trim())
                continue
            }
            BULLET.matches(line) || NUMBERED.matches(line) -> {
                flush()
                val numbered = !BULLET.matches(line)
                val start = if (numbered) NUMBERED.find(line)!!.groupValues[1].toInt() else 1
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i]
                    val item = if (numbered) NUMBERED.find(l)?.groupValues?.get(2) else BULLET.find(l)?.groupValues?.get(1)
                    when {
                        item != null -> items += item.trim()
                        // An indented line carries on the item above.
                        l.isNotBlank() && l.startsWith("  ") && items.isNotEmpty() -> items[items.lastIndex] += " " + l.trim()
                        // A blank line between items keeps the list going.
                        l.isBlank() && i + 1 < lines.size && (if (numbered) NUMBERED.matches(lines[i + 1]) else BULLET.matches(lines[i + 1])) -> {}
                        else -> break
                    }
                    i++
                }
                val rows = items.map(::timeRow)
                parts += when {
                    rows.all { it != null } -> ReplyPart.Agenda(rows.filterNotNull())
                    numbered -> ReplyPart.Numbered(start, items)
                    else -> ReplyPart.Bullets(items)
                }
                continue
            }
            else -> {
                // Timed lines and ordinary ones written together become separate blocks.
                if (paragraph.isNotEmpty() && (timeRow(paragraph.last()) != null) != (timeRow(trimmed) != null)) flush()
                paragraph += trimmed
            }
        }
        i++
    }
    flush()
    // Agendas split only by how they were written (some lines bulleted, some not) are one agenda.
    val merged = mutableListOf<ReplyPart>()
    for (part in parts) {
        val last = merged.lastOrNull()
        if (part is ReplyPart.Agenda && last is ReplyPart.Agenda) merged[merged.lastIndex] = ReplyPart.Agenda(last.rows + part.rows) else merged += part
    }
    return merged.ifEmpty { listOf(ReplyPart.Paragraph(text.trim())) }
}
