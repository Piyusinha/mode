package com.piyush.mode.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Mode's type scale, the same names and sizes as the iPhone's ModeTextStyle. Titles and section
 * headings use Bricolage Grotesque (SIL Open Font License, BRICOLAGE-OFL.txt), heavy and set 2%
 * tighter, its optical size matched to the size; everything else uses the phone's own sans
 * (Roboto on most phones). Every style follows the phone's font size setting (sp).
 */
public enum class ModeTextStyle(
    internal val size: Float,
    internal val weight: Int,
    internal val display: Boolean,
    internal val lineHeight: Float,
) {
    /** 34 heavy display: a top-level page (Settings, Tasks, Memory). */
    LargeTitle(34f, 800, true, 40f),
    /** 32 heavy display: a setup step or a page's headline. */
    Title(32f, 800, true, 36f),
    /** 30 heavy display: the Mo tab's name. */
    TabTitle(30f, 800, true, 34f),
    /** 24 bold display: the words heard while holding the talk button. */
    Heard(24f, 700, true, 29f),
    /** 28 heavy display: a page's heading. */
    Heading(28f, 800, true, 33f),
    /** 26 heavy display: a conversation's title, a sheet's title. */
    Title2(26f, 800, true, 31f),
    /** 20 bold display: the running task's name, Mo's line on a card. */
    Title3(20f, 700, true, 24f),
    /** A "Did you know?" tip's title: small enough for the card to stay short. */
    TipTitle(16f, 700, true, 20f),
    /** 18 bold display: section headings (Suggested, Recent, You, AI). */
    Section(18f, 700, true, 22f),
    /** 17 medium: a row that stands out, a primary button. */
    Headline(17f, 500, false, 22f),
    /** 17: replies and reading text. */
    Body(17f, 400, false, 24f),
    BodyEmphasis(17f, 500, false, 24f),
    /** 16: rows, fields, secondary buttons. */
    Callout(16f, 400, false, 21f),
    CalloutEmphasis(16f, 500, false, 21f),
    /** 15: a greeting, a small button. */
    Subheadline(15f, 400, false, 20f),
    SubheadlineEmphasis(15f, 500, false, 20f),
    /** 13: the second line of a row, notes under a card. */
    Footnote(13f, 400, false, 18f),
    FootnoteEmphasis(13f, 500, false, 18f),
    /** 12 and 11: labels, tab titles, badges. */
    Caption(12f, 400, false, 16f),
    CaptionEmphasis(11f, 700, false, 13f),
    /** 34 heavy display: Mode's spoken question in talking mode. */
    Question(34f, 800, true, 39f),
    /** 30 bold display: the words heard while listening. */
    LiveWords(30f, 700, true, 35f),
    /** 26 bold display: the answer heard. */
    AnswerWords(26f, 700, true, 31f),
    /** 24 heavy display: talking mode's notice. */
    SheetTitle(24f, 800, true, 29f),
    /** 18 semibold: what Mode says, the user's words in a bubble. */
    Spoken(18f, 600, false, 24f),
    /** 14: a hint, a status pill. */
    Small(14f, 400, false, 19f),
    SmallEmphasis(14f, 600, false, 19f),
    ;

    /** The Compose text style, in [color]. */
    public fun style(color: Color): TextStyle = TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = FontWeight(weight),
        // One family for every word: Bricolage, its optical size following the text size (the iPhone's body is Bricolage too).
        fontFamily = bricolage(size, weight),
        letterSpacing = if (display) (-0.02).em else 0.em,
    )
}

/** Bricolage at [weight], its optical size matched to [size] (12–96), as on the iPhone. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun bricolage(size: Float, weight: Int): FontFamily = FontFamily(
    Font(
        R.font.bricolage_grotesque,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("opsz", size.coerceIn(12f, 96f)),
        ),
    ),
)

/** Text in one of Mode's styles: the only way screens draw words. */
@Composable
public fun ModeText(
    text: String,
    style: ModeTextStyle,
    modifier: Modifier = Modifier,
    color: Color = Mode.colors.textPrimary,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    /** One line that shrinks to fit (down to [ModeTextFit.min] of its size) before it ends in "…": a row's value. */
    fit: Boolean = false,
) {
    val textStyle = style.style(color).let { if (align != null) it.copy(textAlign = align) else it }
    BasicText(
        text = text,
        modifier = modifier,
        style = textStyle,
        maxLines = if (fit) 1 else maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (fit) {
            androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = textStyle.fontSize * ModeTextFit.min, maxFontSize = textStyle.fontSize, stepSize = ModeTextFit.step,
            )
        } else null,
    )
}

/** How far a fitted line may shrink, and in what steps. */
public object ModeTextFit {
    public const val min: Float = 0.75f
    public val step: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp)
}
