package com.piyush.mode.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow

/** A block of Mode's reply (ReplyPart in the shared code, drawn here). */
public sealed interface ModeReplyBlock {
    public data class Paragraph(val text: String) : ModeReplyBlock
    public data class Heading(val level: Int, val text: String) : ModeReplyBlock
    public data class Bullets(val items: List<String>) : ModeReplyBlock
    public data class Numbered(val start: Int, val items: List<String>) : ModeReplyBlock
    public data class Agenda(val rows: List<ModeAgendaRow>) : ModeReplyBlock
    public data class Table(val header: List<String>, val rows: List<List<String>>) : ModeReplyBlock
    public data class Quote(val text: String) : ModeReplyBlock
    public data class Code(val text: String) : ModeReplyBlock
    public data object Divider : ModeReplyBlock
}

/** One line of a day's plan: its time (or "All day"), when it ends, and what. */
public data class ModeAgendaRow(val time: String, val end: String?, val text: String, val allDay: Boolean)

/** A full reply in a chat, or a one-line summary (compact) on smaller surfaces. */
public enum class ModeReplyDensity { Regular, Compact }

/** Mode's reply, block by block; words can be selected and copied. */
@Composable
public fun ModeReply(blocks: List<ModeReplyBlock>, modifier: Modifier = Modifier, density: ModeReplyDensity = ModeReplyDensity.Regular) {
    val regular = density == ModeReplyDensity.Regular
    SelectionContainer(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(if (regular) ModeSpacing.chatGap else ModeSpacing.xs)) {
            blocks.forEachIndexed { index, block -> Block(block, index == 0, density) }
        }
    }
}

@Composable
private fun Block(block: ModeReplyBlock, first: Boolean, density: ModeReplyDensity) {
    val c = Mode.colors
    val regular = density == ModeReplyDensity.Regular
    val body = if (regular) ModeTextStyle.Body else ModeTextStyle.Callout
    when (block) {
        is ModeReplyBlock.Paragraph -> ModeInlineText(
            block.text, if (first && !regular) ModeTextStyle.Subheadline else body,
            maxLines = if (regular) Int.MAX_VALUE else 1,
        )
        is ModeReplyBlock.Heading -> ModeInlineText(
            block.text, if (block.level <= 2 && regular) ModeTextStyle.Title2 else ModeTextStyle.Headline,
            Modifier.padding(top = if (first) ModeSpacing.none else ModeSpacing.xs),
        )
        is ModeReplyBlock.Bullets -> ListBlock(block.items, body) { _ -> ModeText(BULLET, ModeTextStyle.BodyEmphasis, color = c.tint) }
        is ModeReplyBlock.Numbered -> ListBlock(block.items, body) { i ->
            ModeText("${block.start + i}$NUMBER_SUFFIX", ModeTextStyle.BodyEmphasis, color = c.textSecondary)
        }
        is ModeReplyBlock.Agenda -> ModeAgenda(block.rows, plain = !regular)
        is ModeReplyBlock.Table -> TableBlock(block.header, block.rows)
        is ModeReplyBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
            Box(Modifier.width(ModeSize.quoteBar).fillMaxHeight().background(c.tint, Capsule))
            ModeInlineText(block.text, ModeTextStyle.Body, color = c.textSecondary)
        }
        is ModeReplyBlock.Code -> Box(
            Modifier.fillMaxWidth().background(c.fill, RoundedCornerShape(ModeRadius.chip)).horizontalScroll(rememberScrollState()),
        ) {
            BasicText(block.text, Modifier.padding(ModeSpacing.m), style = ModeTextStyle.Callout.style(c.textPrimary).copy(fontFamily = FontFamily.Monospace))
        }
        ModeReplyBlock.Divider -> ModeHairline(Modifier.padding(vertical = ModeSpacing.xs))
    }
}

@Composable
private fun ListBlock(items: List<String>, style: ModeTextStyle, mark: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        items.forEachIndexed { i, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                Box(Modifier.defaultMinSize(minWidth = ModeSize.listMark)) { mark(i) }
                ModeInlineText(item, style, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableBlock(header: List<String>, rows: List<List<String>>) {
    val c = Mode.colors
    Box(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(ModeRadius.chip)).horizontalScroll(rememberScrollState())) {
        Column(Modifier.padding(ModeSpacing.l).width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
                for (i in 0 until columns) ModeInlineText(header.getOrElse(i) { "" }, ModeTextStyle.Subheadline, Modifier.weight(1f), color = c.textSecondary)
            }
            rows.forEach { row ->
                ModeHairline()
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
                    for (i in 0 until columns) ModeInlineText(row.getOrElse(i) { "" }, ModeTextStyle.Callout, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A day's plan: times in a column, what happens beside them. */
@Composable
public fun ModeAgenda(rows: List<ModeAgendaRow>, modifier: Modifier = Modifier, plain: Boolean = false) {
    val c = Mode.colors
    Column(modifier.padding(horizontal = if (plain) ModeSpacing.none else ModeSpacing.xs), verticalArrangement = Arrangement.spacedBy(if (plain) ModeSpacing.xxs else ModeSpacing.s)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (plain) ModeSpacing.m else ModeSpacing.chatGap)) {
                Column(Modifier.defaultMinSize(minWidth = if (plain) ModeSpacing.none else ModeSize.agendaTimeChat)) {
                    ModeText(
                        row.time,
                        when {
                            row.allDay -> ModeTextStyle.FootnoteEmphasis
                            else -> ModeTextStyle.CalloutEmphasis
                        },
                        color = if (row.allDay) c.textSecondary else c.textPrimary,
                    )
                    if (!plain && row.end != null) ModeText(row.end, ModeTextStyle.Caption, color = c.textSecondary)
                }
                ModeInlineText(row.text, ModeTextStyle.Callout, Modifier.weight(1f), maxLines = if (plain) 1 else Int.MAX_VALUE)
            }
        }
    }
}

/** Text with inline Markdown (bold, italic, code, links in Mo's colour). */
@Composable
public fun ModeInlineText(
    markdown: String,
    style: ModeTextStyle,
    modifier: Modifier = Modifier,
    color: Color = Mode.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
) {
    val link = Mode.colors.tint
    val text = remember(markdown, link) { ModeInlineMarkdown.parse(markdown, link) }
    BasicText(
        text, modifier, style = style.style(color),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** A light sweep across working words while Mode works (off when the user asks for less motion). */
@Composable
public fun Modifier.modeShimmer(): Modifier {
    val travel = with(LocalDensity.current) { ModeSize.shimmerTravel.toPx() }
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -travel, travel, infiniteRepeatable(tween(ModeMotion.shimmerMs, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    )
    val shine = Mode.colors.textPrimary.copy(alpha = ModeOpacity.shimmer)
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val width = size.width * ModeOpacity.shimmerWidth
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, shine, Color.Transparent), startX = sweep, endX = sweep + width),
            topLeft = Offset.Zero, size = size, blendMode = BlendMode.SrcAtop,
        )
    }
}

private const val BULLET = "•"
private const val NUMBER_SUFFIX = "."
