package com.piyush.mode.app.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import com.piyush.mode.app.R
import com.piyush.mode.app.screens.settings.TalkResultRow
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeIconImage
import com.piyush.mode.design.ModeIconSize
import com.piyush.mode.design.ModeListeningStrip
import com.piyush.mode.design.ModeMiniScreen
import com.piyush.mode.design.ModeMo
import com.piyush.mode.design.ModeOverlayBar
import com.piyush.mode.design.ModeOverlayBarKind
import com.piyush.mode.design.ModeOverlayButton
import com.piyush.mode.design.ModeOverlayChip
import com.piyush.mode.design.ModeOverlayHeader
import com.piyush.mode.design.ModeOverlayLine
import com.piyush.mode.design.ModeOverlaySheet
import com.piyush.mode.design.ModeOverlayStep
import com.piyush.mode.design.ModeOverlayTone
import com.piyush.mode.design.ModeScreenGlow
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeStepStream
import com.piyush.mode.design.ModeTalkLiveWords
import com.piyush.mode.design.ModeTalkOption
import com.piyush.mode.design.ModeTalkTypeBox
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeVoiceButton
import com.piyush.mode.design.ModeVoiceButtonStyle
import com.piyush.mode.design.MoMood

/** What the user did in the overlay. */
internal sealed interface OverlayEvent {
    data object Close : OverlayEvent
    data object Slim : OverlayEvent
    data object Expand : OverlayEvent
    data class Say(val text: String) : OverlayEvent
    data class Send(val text: String, val questionId: String?) : OverlayEvent
    data object TypeInstead : OverlayEvent
    data class Answer(val questionId: String, val text: String) : OverlayEvent
    data class EnteredIt(val questionId: String) : OverlayEvent
    data object TakeOver : OverlayEvent
    data object GiveBack : OverlayEvent
    data object Stop : OverlayEvent
    data object Pause : OverlayEvent
    data object Resume : OverlayEvent
    data class OpenChat(val conversationId: String) : OverlayEvent
    data object AllowMic : OverlayEvent
}

/** The sheet or the slim bar, whichever the state calls for. */
@Composable
internal fun OverlaySheet(view: OverlayView, screen: ImageBitmap?, level: Float, speaking: Boolean, onEvent: (OverlayEvent) -> Unit) {
    when (view) {
        is OverlayView.Working -> if (view.slim) {
            ModeOverlayBar(
                ModeOverlayBarKind.Working,
                top = if (view.parts > 0) stringResource(R.string.overlay_bar_live, minOf(view.done + 1, view.parts), view.parts) else stringResource(R.string.overlay_bar_live_plain),
                main = view.now.ifBlank { view.task },
                action = stringResource(R.string.overlay_stop),
                onMain = { onEvent(OverlayEvent.Expand) },
                onAction = { onEvent(OverlayEvent.Stop) },
                expandLabel = stringResource(R.string.overlay_expand),
            )
            return
        }
        is OverlayView.Turn -> {
            ModeOverlayBar(
                ModeOverlayBarKind.Turn, stringResource(R.string.overlay_turn), view.what, stringResource(R.string.overlay_entered),
                onMain = {}, onAction = { onEvent(OverlayEvent.EnteredIt(view.questionId)) },
            )
            return
        }
        OverlayView.InControl -> {
            ModeOverlayBar(
                ModeOverlayBarKind.Turn, stringResource(R.string.overlay_in_control), stringResource(R.string.overlay_bar_control),
                stringResource(R.string.overlay_give_back), onMain = {}, onAction = { onEvent(OverlayEvent.GiveBack) },
            )
            return
        }
        is OverlayView.OnScreen -> return
        else -> Unit
    }
    ModeOverlaySheet {
        Header(view, speaking, onEvent)
        Body(view, screen, onEvent)
        Strip(view, level, speaking, onEvent)
    }
}

@Composable
private fun Header(view: OverlayView, speaking: Boolean, onEvent: (OverlayEvent) -> Unit) {
    val (mood, status, tone, sub) = when (view) {
        is OverlayView.Greet -> Quad(
            if (view.listening) MoMood.Listening else MoMood.Idle,
            stringResource(if (speaking || !view.listening) R.string.overlay_speaking else R.string.overlay_listening),
            ModeOverlayTone.Live, stringResource(R.string.overlay_sub_ready),
        )
        is OverlayView.Heard -> Quad(MoMood.Listening, stringResource(R.string.overlay_hearing), ModeOverlayTone.Live, stringResource(R.string.overlay_sub_words))
        is OverlayView.Working -> Quad(
            MoMood.Working, stringResource(if (view.onScreen) R.string.overlay_working_screen else R.string.overlay_working_bg),
            ModeOverlayTone.Working, view.task,
        )
        is OverlayView.Question -> Quad(MoMood.YourTurn, stringResource(R.string.overlay_asking), ModeOverlayTone.Asking, view.task)
        is OverlayView.Result -> Quad(MoMood.Done, stringResource(R.string.overlay_done), ModeOverlayTone.Done, view.task)
        is OverlayView.Failed -> Quad(MoMood.Stuck, stringResource(R.string.overlay_failed), ModeOverlayTone.Problem, view.task)
        is OverlayView.Typing -> Quad(MoMood.Idle, stringResource(R.string.overlay_type), ModeOverlayTone.Working, stringResource(R.string.overlay_sub_ready))
        OverlayView.NeedsMic -> Quad(MoMood.YourTurn, stringResource(R.string.overlay_mic_title), ModeOverlayTone.Asking, "")
        else -> Quad(MoMood.Idle, "", ModeOverlayTone.Working, "")
    }
    ModeOverlayHeader(
        mood, status, tone, sub, stringResource(R.string.overlay_slim), stringResource(R.string.overlay_close),
        onSlim = { onEvent(OverlayEvent.Slim) }, onClose = { onEvent(OverlayEvent.Close) },
    )
}

private data class Quad(val mood: MoMood, val status: String, val tone: ModeOverlayTone, val sub: String)

@Composable
private fun Body(view: OverlayView, screen: ImageBitmap?, onEvent: (OverlayEvent) -> Unit) {
    val c = Mode.colors
    when (view) {
        is OverlayView.Greet -> {
            if (view.line.isNotBlank()) ModeOverlayLine(view.line)
            if (view.chips.isNotEmpty()) Chips(view.chips) { onEvent(OverlayEvent.Say(it)) }
        }
        is OverlayView.Heard -> {
            val words = view.words.trim().split(' ')
            ModeTalkLiveWords(words.dropLast(1).joinToString(" "), words.last(), ModeTextStyle.LiveWords, c.tint)
            ModeText(stringResource(R.string.overlay_hint_pause), ModeTextStyle.Subheadline, color = c.textSecondary)
        }
        is OverlayView.Working -> {
            ModeText(view.task, ModeTextStyle.Title3, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
                ModeStepStream(view.steps.map { ModeOverlayStep(it.text, time(it.atSeconds), it.now) }, Modifier.weight(1f))
                if (!view.onScreen) ModeMiniScreen(screen, stringResource(R.string.overlay_hidden))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                if (view.onScreen) {
                    ModeVoiceButton(stringResource(R.string.overlay_resume), ModeVoiceButtonStyle.Ink, { onEvent(OverlayEvent.Resume) }, Modifier.weight(2f))
                } else {
                    ModeVoiceButton(stringResource(R.string.overlay_take_over), ModeVoiceButtonStyle.Raised, { onEvent(OverlayEvent.TakeOver) }, Modifier.weight(2f))
                }
                ModeVoiceButton(stringResource(R.string.overlay_stop), ModeVoiceButtonStyle.Danger, { onEvent(OverlayEvent.Stop) }, Modifier.weight(1f))
            }
        }
        is OverlayView.Question -> {
            ModeOverlayLine(view.question)
            val options = view.options.ifEmpty { if (view.yesNo) listOf(stringResource(R.string.overlay_yes), stringResource(R.string.overlay_no)) else emptyList() }
            if (options.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                    options.forEach { option ->
                        ModeTalkOption(
                            option, chosen = view.words.isNotBlank() && option.lowercase().startsWith(view.words.trim().lowercase()),
                            onClick = { onEvent(OverlayEvent.Answer(view.questionId, if (view.yesNo && view.options.isEmpty()) yesNoAnswer(option, options) else option)) },
                        )
                    }
                }
            }
            if (view.words.isNotBlank()) ModeTalkLiveWords(view.words, "", ModeTextStyle.AnswerWords, c.info)
        }
        is OverlayView.Result -> {
            if (view.headline.isNotBlank()) ModeOverlayLine(view.headline)
            view.rows.take(MAX_ROWS).forEach { ResultRow(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                view.conversationId?.let { id -> ModeOverlayChip(stringResource(R.string.overlay_open_chat), { onEvent(OverlayEvent.OpenChat(id)) }) }
            }
            if (view.next.isNotBlank()) ModeOverlayLine(view.next, quiet = true)
        }
        is OverlayView.Failed -> {
            ModeOverlayLine(view.line)
            val offer = view.offer
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                if (offer != null) {
                    ModeOverlayChip(offer.yes, { onEvent(OverlayEvent.Say(offer.yes)) })
                    ModeOverlayChip(offer.no, { onEvent(OverlayEvent.Say(offer.no)) })
                }
                view.conversationId?.let { id -> ModeOverlayChip(stringResource(R.string.overlay_open_chat), { onEvent(OverlayEvent.OpenChat(id)) }) }
            }
        }
        is OverlayView.Typing -> {
            var text by remember { mutableStateOf("") }
            ModeTalkTypeBox(
                text, { text = it }, stringResource(if (view.questionId != null) R.string.overlay_answer_prompt else R.string.overlay_type_prompt),
                { if (text.isNotBlank()) onEvent(OverlayEvent.Send(text, view.questionId)) },
            )
            ModeVoiceButton(stringResource(R.string.overlay_send), ModeVoiceButtonStyle.Ink, {
                if (text.isNotBlank()) onEvent(OverlayEvent.Send(text, view.questionId))
            }, enabled = text.isNotBlank())
        }
        OverlayView.NeedsMic -> {
            ModeText(stringResource(R.string.overlay_mic_body), ModeTextStyle.Subheadline, color = c.textSecondary)
            ModeVoiceButton(stringResource(R.string.overlay_mic_allow), ModeVoiceButtonStyle.Info, { onEvent(OverlayEvent.AllowMic) }, icon = ModeIcon.Microphone)
        }
        else -> Unit
    }
}

/** The listening strip: always there while Mode waits for the user, so nobody has to press anything. */
@Composable
private fun Strip(view: OverlayView, level: Float, speaking: Boolean, onEvent: (OverlayEvent) -> Unit) {
    val (listening, hint, answering) = when (view) {
        is OverlayView.Greet -> Triple(view.listening || speaking, R.string.overlay_hint_start, false)
        is OverlayView.Heard -> Triple(true, R.string.overlay_hint_pause, false)
        is OverlayView.Question -> Triple(true, R.string.overlay_hint_answer, true)
        is OverlayView.Result -> Triple(view.listening || speaking, R.string.overlay_hint_next, false)
        is OverlayView.Failed -> Triple(view.listening || speaking, R.string.overlay_hint_failed, false)
        else -> Triple(false, 0, false)
    }
    if (!listening) return
    ModeListeningStrip(
        stringResource(if (speaking) R.string.overlay_hint_speaking else hint), if (speaking) 0f else level, answering,
        stringResource(R.string.overlay_type), { onEvent(OverlayEvent.TypeInstead) },
    )
}

@Composable
private fun Chips(chips: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        chips.forEach { ModeOverlayChip(it, { onPick(it) }) }
    }
}

@Composable
private fun ResultRow(row: TalkResultRow) {
    val c = Mode.colors
    Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        ModeIconImage(if (row.done) ModeIcon.Checkmark else ModeIcon.Close, ModeIconSize.small, if (row.done) c.success else c.danger)
        Column(Modifier.weight(1f)) {
            ModeText(row.title, ModeTextStyle.CalloutEmphasis, maxLines = 2)
            if (row.detail.isNotBlank()) ModeText(row.detail, ModeTextStyle.Footnote, color = c.textSecondary, maxLines = 2)
        }
    }
}

/** On the user's own screen: the Mo button with what it's doing, at the edge. */
@Composable
internal fun OverlayButton(bubble: String, onEvent: (OverlayEvent) -> Unit) {
    Box(Modifier.padding(ModeSpacing.m)) {
        ModeOverlayButton(bubble, stringResource(R.string.overlay_button), { onEvent(OverlayEvent.Pause) })
    }
}

/** The glowing edge round the screen while Mode drives it. */
@Composable
internal fun OverlayGlow() {
    ModeScreenGlow(Modifier.fillMaxSize())
}

/** "Yes" / "No" as the answers the runner expects. */
private fun yesNoAnswer(option: String, options: List<String>): String =
    if (option == options.first()) com.piyush.mode.core.platform.QuestionActions.ANSWER_YES else com.piyush.mode.core.platform.QuestionActions.ANSWER_NO

@Composable
private fun time(seconds: Long): String = stringResource(R.string.overlay_time, (seconds / SECONDS_IN_MINUTE).toInt(), (seconds % SECONDS_IN_MINUTE).toInt())

private const val MAX_ROWS = 3
private const val SECONDS_IN_MINUTE = 60L

/** Keeps the sheet a sensible width on wide screens. */
internal val SheetWidth = Modifier.fillMaxWidth()
internal val ButtonSize = ModeSize.overlayButton
