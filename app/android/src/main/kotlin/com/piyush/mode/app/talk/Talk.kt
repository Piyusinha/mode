package com.piyush.mode.app.talk

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.ListenPresenter
import com.piyush.mode.app.screens.settings.ListenUiState
import com.piyush.mode.app.screens.settings.TalkPhase
import com.piyush.mode.app.screens.settings.TalkTaskCard
import com.piyush.mode.app.screens.settings.TalkTurn
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.voice.VoiceLevel
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeIconButton
import com.piyush.mode.design.ModeIconImage
import com.piyush.mode.design.ModeIconSize
import com.piyush.mode.design.ModeLiveLabel
import com.piyush.mode.design.ModeLogo
import com.piyush.mode.design.ModeLogoImage
import com.piyush.mode.design.ModeRadius
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeChipFlow
import com.piyush.mode.design.ModeTalkBubble
import com.piyush.mode.design.ModeTalkCardStatus
import com.piyush.mode.design.ModeTalkLiveWords
import com.piyush.mode.design.ModeTalkMicAsk
import com.piyush.mode.design.ModeTalkMicButton
import com.piyush.mode.design.ModeTalkMicOff
import com.piyush.mode.design.ModeTalkMo
import com.piyush.mode.design.ModeTalkMoLine
import com.piyush.mode.design.ModeTalkNext
import com.piyush.mode.design.ModeTalkOption
import com.piyush.mode.design.ModeTalkRow
import com.piyush.mode.design.ModeTalkSays
import com.piyush.mode.design.ModeTalkSideButton
import com.piyush.mode.design.ModeTalkStatus
import com.piyush.mode.design.ModeTalkStep
import com.piyush.mode.design.ModeTalkTaskCard
import com.piyush.mode.design.ModeTalkTone
import com.piyush.mode.design.ModeTalkTypeBox
import com.piyush.mode.design.ModeTalkWave
import com.piyush.mode.design.ModeTalkWorking
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeVoiceButton
import com.piyush.mode.design.ModeVoiceButtonStyle
import com.piyush.mode.design.ModeVoiceNotice
import com.piyush.mode.design.MoMood
import com.piyush.mode.design.modeShadow
import kotlinx.coroutines.delay

/** What opened talking mode: a mic (in a chat, or Ask), the assistant, or a spoken question in a running task. */
data class TalkRequest(val conversationId: String? = null, val entry: String = ENTRY_ASSISTANT, val forQuestion: Boolean = false) {
    companion object {
        const val ENTRY_ASSISTANT = "assistant"
        const val ENTRY_ASK = "ask_mic"
        const val ENTRY_CHAT = "chat_mic"
    }
}

/** Talking mode as the screen draws it. */
data class TalkState(
    val phase: TalkPhase,
    val greeting: String,
    val turns: List<TalkTurn>,
    val hasDone: Boolean,
    val settled: String,
    val forming: String,
    val task: String,
    val reply: String,
    val working: String,
    val question: String,
    val options: List<String>,
    val answer: String,
    val answerReply: String,
    val micDenied: Boolean,
    /** "Mode couldn't hear with Deepgram", why in a sentence, and whether it was the listener. */
    val notice: String?,
    val noticeMessage: String?,
    val noticeHearing: Boolean,
    /** The option the words heard so far point to, while answering. */
    val heardOption: String?,
)

/** What the user did in talking mode. */
sealed interface TalkEvent {
    data object AllowMic : TalkEvent
    data object OpenSettings : TalkEvent
    data object Done : TalkEvent
    data object Type : TalkEvent
    data object Send : TalkEvent
    data class Answer(val option: String) : TalkEvent
    data class Pick(val answer: String) : TalkEvent
    data object ThatsAll : TalkEvent
    data object Later : TalkEvent
    data object Stop : TalkEvent
    data object Close : TalkEvent
    data object TryAgain : TalkEvent
    data object VoiceSettings : TalkEvent
    data class OpenChat(val conversationId: String) : TalkEvent
    data class WhatWentWrong(val taskId: String) : TalkEvent
}

/**
 * Talking mode, from "Start Mode" to done (boards "Talking mode"); also a spoken question in a running task, when
 * Mode comes forward to ask it. Closes itself when the user closes it or the spoken task finishes.
 */
@Composable
fun TalkRoute(
    graph: ModeGraph,
    request: TalkRequest,
    openSettings: () -> Unit,
    onVoiceSettings: (hearing: Boolean) -> Unit,
    onOpenChat: (String) -> Unit,
    onWhatWentWrong: (String) -> Unit,
    onClose: () -> Unit,
) {
    val presenter: ListenPresenter = rememberPresenter("talk") {
        if (request.forQuestion) graph.screens.talkQuestion() else graph.screens.listen(request.conversationId, request.entry)
    }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val level by VoiceLevel.level.collectAsStateWithLifecycle()
    val speaking by VoiceLevel.speaking.collectAsStateWithLifecycle()
    val cantHear by VoiceLevel.cantHear.collectAsStateWithLifecycle()
    val words = rememberWords()
    var typed by rememberSaveable { mutableStateOf("") }
    var typingAnswer by rememberSaveable { mutableStateOf(false) }
    var noticeSeen by rememberSaveable { mutableStateOf(false) }
    val state = remember(ui) { map(ui, words) }

    fun close() {
        presenter.dismiss()
        onClose()
    }
    LaunchedEffect(Unit) { if (!request.forQuestion) presenter.begin() }
    LaunchedEffect(ui.finished) { if (ui.finished) close() }
    BackHandler { close() }

    TalkScreen(state, level, speaking, cantHear, typingAnswer, noticeSeen, typed, { typed = it }) { event ->
        when (event) {
            TalkEvent.AllowMic -> presenter.allowMic()
            TalkEvent.OpenSettings -> openSettings()
            TalkEvent.Done -> presenter.finish()
            TalkEvent.Type -> when {
                state.notice != null && state.phase == TalkPhase.Typing -> noticeSeen = true
                state.phase == TalkPhase.Asking || state.phase == TalkPhase.Answering -> {
                    typingAnswer = true
                    presenter.later()
                }
                else -> presenter.typeInstead()
            }
            TalkEvent.Send -> {
                val text = typed
                typed = ""
                if (typingAnswer) {
                    typingAnswer = false
                    presenter.answer(text)
                } else {
                    presenter.submitTyped(text)
                }
            }
            is TalkEvent.Answer -> presenter.answer(event.option)
            is TalkEvent.Pick -> presenter.pick(event.answer)
            TalkEvent.ThatsAll -> presenter.thatsAll()
            TalkEvent.Later -> {
                presenter.later()
                close()
            }
            TalkEvent.Stop -> {
                presenter.stop()
                close()
            }
            TalkEvent.Close -> close()
            TalkEvent.TryAgain -> {
                noticeSeen = false
                presenter.begin()
            }
            TalkEvent.VoiceSettings -> {
                val hearing = state.noticeHearing
                close()
                onVoiceSettings(hearing)
            }
            is TalkEvent.OpenChat -> {
                close()
                onOpenChat(event.conversationId)
            }
            is TalkEvent.WhatWentWrong -> {
                close()
                onWhatWentWrong(event.taskId)
            }
        }
    }
}

private fun map(s: ListenUiState, w: Words): TalkState {
    // The newest word is still forming: it shows lighter until the next one comes.
    val heard = s.partial.split(' ').filter { it.isNotBlank() }
    val provider = s.voiceNotice?.let { w.voiceProvider(it.provider) }
    return TalkState(
        phase = s.phase, greeting = s.greeting, turns = s.turns, hasDone = s.doneSoFar.isNotEmpty(),
        settled = heard.dropLast(1).joinToString(" "), forming = heard.lastOrNull().orEmpty(),
        task = s.task, reply = s.reply, working = s.working, question = s.question, options = s.options,
        answer = s.answer, answerReply = s.answerReply, micDenied = s.micDenied,
        notice = s.voiceNotice?.let { w.get(if (it.hearing) R.string.voice_couldnt_hear_with else R.string.voice_couldnt_speak_with, provider.orEmpty()) },
        noticeMessage = s.voiceNotice?.let { w.voiceTrouble(it.trouble, it.hearing) },
        noticeHearing = s.voiceNotice?.hearing ?: false,
        heardOption = s.partial.trim().lowercase().takeIf { it.isNotEmpty() }?.let { said -> s.options.firstOrNull { it.lowercase().startsWith(said) } },
    )
}

@Composable
fun TalkScreen(
    state: TalkState,
    level: Float,
    speaking: Boolean,
    cantHear: Boolean,
    typingAnswer: Boolean,
    noticeSeen: Boolean,
    typed: String,
    onTyped: (String) -> Unit,
    onEvent: (TalkEvent) -> Unit,
) {
    val c = Mode.colors
    val phase = state.phase
    val listening = (phase == TalkPhase.Listening || phase == TalkPhase.Answering) && !typingAnswer
    // Hearing failed and nothing started: "Mode couldn't hear you", until the user types instead.
    val hearingFailed = phase == TalkPhase.Typing && state.notice != null && state.noticeHearing && !noticeSeen
    // They've asked for something: the screen is the conversation, with what's happening now at the bottom.
    val inConversation = state.turns.any { it.mine } &&
        phase in setOf(TalkPhase.Greeting, TalkPhase.Listening, TalkPhase.Starting, TalkPhase.Typing) && !hearingFailed
    val scroll = rememberScrollState()
    LaunchedEffect(state.turns, phase, state.forming, inConversation) {
        if (inConversation) {
            delay(SETTLE_MS)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.page).clickable(indication = null, interactionSource = null) {}.systemBarsPadding().imePadding()
            .padding(bottom = ModeSpacing.l),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
    ) {
        Header(state, listening, hearingFailed, inConversation, onEvent)
        val notice = state.notice
        if (notice != null && !hearingFailed) {
            ModeVoiceNotice(notice, stringResource(R.string.voice_update_in_settings), { onEvent(TalkEvent.VoiceSettings) }, Modifier.padding(horizontal = ModeSpacing.screenEdge))
        } else {
            AnimatedVisibility(listening && cantHear, enter = fadeIn(), exit = fadeOut()) {
                CantHear(Modifier.padding(horizontal = ModeSpacing.screenEdge))
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = ModeSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
        ) {
            if (inConversation) Conversation(state, level, speaking, typed, onTyped, onEvent)
            else Page(state, level, speaking, typingAnswer, hearingFailed, typed, onTyped, onEvent)
        }
        Dock(state, inConversation, typingAnswer, hearingFailed, typed, onEvent)
    }
}

@Composable
private fun Header(state: TalkState, listening: Boolean, hearingFailed: Boolean, inConversation: Boolean, onEvent: (TalkEvent) -> Unit) {
    val c = Mode.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
        ModeIconButton(ModeIcon.Close, stringResource(R.string.talk_close), { onEvent(TalkEvent.Close) })
        Box(Modifier.weight(1f), Alignment.Center) {
            if (!hearingFailed) {
                when (state.phase) {
                    TalkPhase.NeedsMic -> ModeTalkStatus(stringResource(R.string.talk_start_mode), null)
                    TalkPhase.Greeting -> ModeTalkStatus(stringResource(R.string.talk_speaking), c.brand)
                    TalkPhase.Listening -> ModeTalkStatus(stringResource(R.string.talk_listening), c.danger)
                    TalkPhase.Starting -> ModeTalkStatus(stringResource(if (inConversation) R.string.talk_working_mic_off else R.string.talk_starting), c.brand)
                    TalkPhase.Asking, TalkPhase.Answering -> ModeTalkStatus(stringResource(R.string.talk_needs_you), c.info, tone = ModeTalkTone.Info)
                    TalkPhase.Answered -> ModeTalkStatus(stringResource(R.string.talk_got_it), null, tone = ModeTalkTone.Success)
                    TalkPhase.Typing -> ModeTalkStatus(stringResource(R.string.talk_type_it), null)
                }
            }
        }
        if (listening || state.phase == TalkPhase.Greeting || state.phase == TalkPhase.Asking) {
            ModeIconButton(ModeIcon.Keyboard, stringResource(R.string.talk_type), { onEvent(TalkEvent.Type) })
        } else {
            Spacer(Modifier.size(ModeSize.touch))
        }
    }
}

@Composable
private fun CantHear(modifier: Modifier) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().background(c.card, RoundedCornerShape(ModeRadius.bubble)).padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIconImage(ModeIcon.Warning, ModeIconSize.small, c.warning)
        ModeText(stringResource(R.string.talk_cant_hear), ModeTextStyle.Subheadline, Modifier.weight(1f))
    }
}

// The conversation: what they asked, Mo's lines, each task's card, and what's happening now at the bottom.

@Composable
private fun ColumnScope.Conversation(state: TalkState, level: Float, speaking: Boolean, typed: String, onTyped: (String) -> Unit, onEvent: (TalkEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
        state.turns.forEach { turn ->
            when (turn.kind) {
                TalkTurn.Kind.Said -> if (turn.mine) ModeTalkBubble(turn.text) else ModeTalkMoLine(turn.text)
                TalkTurn.Kind.Next -> {
                    val offer = turn.offer?.takeIf { turn.id == state.turns.lastOrNull()?.id && state.phase in setOf(TalkPhase.Greeting, TalkPhase.Listening) }
                    ModeTalkNext(
                        turn.text, yes = offer?.yes, no = offer?.no,
                        onYes = { offer?.let { onEvent(TalkEvent.Pick(it.yes)) } }, onNo = { offer?.let { onEvent(TalkEvent.Pick(it.no)) } },
                    )
                }
                TalkTurn.Kind.Task -> turn.card?.let { TaskCard(it, onEvent) }
            }
        }
        Box(Modifier.padding(top = ModeSpacing.xs).fillMaxWidth(), Alignment.Center) {
            when (state.phase) {
                TalkPhase.Greeting, TalkPhase.Listening -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
                    if (state.phase == TalkPhase.Listening && (state.settled.isNotEmpty() || state.forming.isNotEmpty())) {
                        ModeTalkLiveWords(state.settled, state.forming, ModeTextStyle.LiveWords, Mode.colors.brand)
                    }
                    ModeTalkWave(level, speaking || state.phase == TalkPhase.Greeting)
                }
                TalkPhase.Starting -> Unit
                else -> ModeTalkTypeBox(typed, onTyped, stringResource(R.string.talk_type_task), { onEvent(TalkEvent.Send) })
            }
        }
    }
}

/** A task's card: while it works, the header counts up every second. */
@Composable
private fun TaskCard(card: TalkTaskCard, onEvent: (TalkEvent) -> Unit) {
    val words = rememberWords()
    val status = when (card.state) {
        TalkTaskCard.State.Working -> ModeTalkCardStatus.Working
        TalkTaskCard.State.Done -> ModeTalkCardStatus.Done
        TalkTaskCard.State.Partial -> ModeTalkCardStatus.Partial
        TalkTaskCard.State.Failed -> ModeTalkCardStatus.Failed
        TalkTaskCard.State.Stopped -> ModeTalkCardStatus.Stopped
    }
    val elapsed by produceState(0L, card.startedAtMs, status) {
        while (status == ModeTalkCardStatus.Working) {
            value = (System.currentTimeMillis() - card.startedAtMs) / MS
            delay(MS)
        }
    }
    val label = when (status) {
        ModeTalkCardStatus.Working -> words.get(R.string.talk_card_working, clock(elapsed))
        else -> {
            val took = if (card.seconds >= MINUTE) words.get(R.string.talk_took_minutes, (card.seconds / MINUTE).toString(), (card.seconds % MINUTE).toString())
            else words.get(R.string.talk_took_seconds, card.seconds.toString())
            when (status) {
                ModeTalkCardStatus.Done -> words.get(R.string.talk_card_done, took)
                ModeTalkCardStatus.Partial -> words.get(R.string.talk_card_partial, took)
                ModeTalkCardStatus.Failed -> words.get(R.string.talk_card_failed, took)
                else -> words.get(R.string.talk_card_stopped)
            }
        }
    }
    // "2 of 3" while it works (its plan), "Done", or "1 of 2" parts done.
    val chip = when (status) {
        ModeTalkCardStatus.Working -> if (card.parts > 1) words.get(R.string.talk_parts_of, minOf(card.doneParts + 1, card.parts).toString(), card.parts.toString()) else null
        ModeTalkCardStatus.Done -> words.get(R.string.talk_chip_done)
        ModeTalkCardStatus.Partial -> if (card.rows.isEmpty()) null else words.get(R.string.talk_parts_of, card.rows.count { it.done }.toString(), card.rows.size.toString())
        else -> null
    }
    ModeTalkTaskCard(
        status, label, card.title, chip, card.steps.map { ModeTalkStep(it.text, it.time) },
        stringResource(R.string.talk_result), card.headline, card.rows.map { ModeTalkRow(it.title, it.detail, it.done) },
        stringResource(R.string.talk_open_chat), stringResource(R.string.talk_what_went_wrong),
        onOpen = { card.conversationId?.let { onEvent(TalkEvent.OpenChat(it)) } }, onWrong = { onEvent(TalkEvent.WhatWentWrong(card.taskId)) },
    )
}

/** "1:12", while it works. */
private fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / MINUTE}:${(s % MINUTE).toString().padStart(2, '0')}"
}

// One phase at a time, before there's a conversation.

@Composable
private fun Page(
    state: TalkState,
    level: Float,
    speaking: Boolean,
    typingAnswer: Boolean,
    hearingFailed: Boolean,
    typed: String,
    onTyped: (String) -> Unit,
    onEvent: (TalkEvent) -> Unit,
) {
    val c = Mode.colors
    when (state.phase) {
        TalkPhase.NeedsMic -> {
            ModeTalkMicAsk(Modifier.padding(top = ModeSpacing.xxxl + ModeSpacing.s, bottom = ModeSpacing.xxl))
            ModeText(stringResource(R.string.talk_mic_title), ModeTextStyle.Heading, align = TextAlign.Center)
            ModeText(
                stringResource(if (state.micDenied) R.string.talk_mic_denied else R.string.talk_mic_message), ModeTextStyle.Subheadline,
                Modifier.padding(horizontal = ModeSpacing.m), color = c.textSecondary, align = TextAlign.Center,
            )
            if (!state.micDenied) {
                val shape = RoundedCornerShape(ModeRadius.bubble)
                Row(
                    Modifier.padding(top = ModeSpacing.xl).fillMaxWidth().modeShadow(shape).background(c.card, shape)
                        .padding(horizontal = ModeSpacing.m + ModeSpacing.xxs, vertical = ModeSpacing.m),
                    horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeLogoImage(ModeLogo.Android, ModeIconSize.large, c.success)
                    ModeText(stringResource(R.string.talk_mic_model), ModeTextStyle.Small, Modifier.weight(1f), color = c.textSecondary)
                }
            }
        }
        TalkPhase.Greeting, TalkPhase.Listening -> {
            if (state.greeting.isNotEmpty()) {
                // Mode said this out loud as it opened; it stays as a quiet caption.
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs)) {
                    ModeIconImage(ModeIcon.Speaker, ModeIconSize.chevron, c.tint)
                    ModeText(state.greeting, ModeTextStyle.Small, color = c.textSecondary, align = TextAlign.Center)
                }
            }
            ModeTalkMo(if (state.phase == TalkPhase.Greeting) MoMood.Idle else MoMood.Listening, ModeSize.talkMoListening, Modifier.height(ModeSize.talkHalo), level, halo = true)
            ModeTalkWave(level, speaking || state.phase == TalkPhase.Greeting)
            if (state.phase == TalkPhase.Listening) {
                ModeLiveLabel(stringResource(R.string.talk_hearing_live))
                if (state.settled.isEmpty() && state.forming.isEmpty()) {
                    ModeText(stringResource(R.string.talk_say_it), ModeTextStyle.LiveWords, color = c.textSecondary, align = TextAlign.Center)
                } else {
                    ModeTalkLiveWords(state.settled, state.forming, ModeTextStyle.LiveWords, c.brand)
                }
                ModeText(stringResource(R.string.talk_words_appear), ModeTextStyle.Subheadline, color = c.textSecondary, align = TextAlign.Center)
            } else {
                ModeText(stringResource(R.string.talk_mic_opens_after), ModeTextStyle.Subheadline, color = c.textSecondary, align = TextAlign.Center)
            }
        }
        TalkPhase.Starting -> {
            ModeTalkMo(MoMood.Working, ModeSize.talkMo)
            if (state.task.isNotEmpty()) ModeTalkBubble(state.task)
            if (state.reply.isNotEmpty()) ModeTalkSays(stringResource(if (speaking) R.string.talk_mode_speaking else R.string.talk_mode_label), state.reply, speaking)
            ModeTalkWorking(state.working.ifEmpty { stringResource(R.string.talk_working_on_it) }, stringResource(R.string.talk_in_background))
            ModeText(stringResource(R.string.talk_if_needed), ModeTextStyle.Subheadline, Modifier.padding(horizontal = ModeSpacing.s), color = c.textSecondary, align = TextAlign.Center)
        }
        TalkPhase.Asking, TalkPhase.Answering -> {
            ModeTalkMo(MoMood.YourTurn, ModeSize.talkMoAsk, Modifier.padding(bottom = ModeSpacing.s))
            if (state.task.isNotEmpty()) ModeText(state.task, ModeTextStyle.Small, color = c.textSecondary, maxLines = 1)
            ModeText(state.question, ModeTextStyle.Question, align = TextAlign.Center)
            if (state.options.isNotEmpty()) {
                ModeChipFlow(Modifier.padding(vertical = ModeSpacing.s)) {
                    state.options.forEach { option -> ModeTalkOption(option, option == state.heardOption, { onEvent(TalkEvent.Answer(option)) }) }
                }
            }
            if (typingAnswer) {
                ModeTalkTypeBox(typed, onTyped, stringResource(R.string.talk_type_answer), { onEvent(TalkEvent.Send) })
            } else {
                ModeTalkWave(level, speaking, bars = QUESTION_BARS, height = ModeSize.talkWaveQuestion, color = c.info)
                if (state.phase == TalkPhase.Answering && (state.settled.isNotEmpty() || state.forming.isNotEmpty())) {
                    ModeTalkLiveWords(state.settled, state.forming, ModeTextStyle.AnswerWords, c.info)
                }
                ModeText(stringResource(if (state.options.isEmpty()) R.string.talk_say_answer else R.string.talk_say_or_tap), ModeTextStyle.Subheadline, color = c.textSecondary)
            }
        }
        TalkPhase.Answered -> {
            ModeTalkMo(MoMood.Done, ModeSize.talkMo)
            if (state.answer.isNotEmpty()) ModeTalkBubble(state.answer)
            if (state.answerReply.isNotEmpty()) ModeTalkSays(stringResource(if (speaking) R.string.talk_mode_speaking else R.string.talk_mode_label), state.answerReply, speaking)
            if (state.working.isNotEmpty()) ModeTalkWorking(state.working, stringResource(R.string.talk_in_background))
            ModeText(stringResource(R.string.talk_leaves_soon), ModeTextStyle.Subheadline, color = c.textSecondary, align = TextAlign.Center)
        }
        TalkPhase.Typing -> if (hearingFailed) {
            // Mode couldn't hear with the chosen listener, so nothing started.
            ModeTalkMo(MoMood.Stuck, ModeSize.talkMoNotice, Modifier.padding(top = ModeSpacing.xxxl + ModeSpacing.xxl, bottom = ModeSpacing.xxxl))
            ModeText(stringResource(R.string.talk_couldnt_hear_you), ModeTextStyle.SheetTitle, align = TextAlign.Center)
            state.noticeMessage?.let { ModeText(it, ModeTextStyle.Subheadline, color = c.textSecondary, align = TextAlign.Center) }
            state.notice?.let {
                ModeVoiceNotice(it, stringResource(R.string.voice_update_in_settings), { onEvent(TalkEvent.VoiceSettings) }, Modifier.padding(top = ModeSpacing.xl))
            }
        } else {
            ModeTalkTypeBox(typed, onTyped, stringResource(R.string.talk_type_task), { onEvent(TalkEvent.Send) })
        }
    }
}

@Composable
private fun Dock(state: TalkState, inConversation: Boolean, typingAnswer: Boolean, hearingFailed: Boolean, typed: String, onEvent: (TalkEvent) -> Unit) {
    val canSend = typed.isNotBlank()
    Box(Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.screenEdge), Alignment.Center) {
        when (state.phase) {
            TalkPhase.NeedsMic -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
                if (state.micDenied) {
                    ModeVoiceButton(stringResource(R.string.talk_open_settings), ModeVoiceButtonStyle.Ink, { onEvent(TalkEvent.OpenSettings) }, height = ModeSize.primaryButton)
                } else {
                    ModeVoiceButton(stringResource(R.string.talk_allow_mic), ModeVoiceButtonStyle.Info, { onEvent(TalkEvent.AllowMic) }, icon = ModeIcon.Microphone, height = ModeSize.primaryButton)
                }
                Box(
                    Modifier.defaultMinSize(minHeight = ModeSize.touch).clickable(role = Role.Button, indication = null, interactionSource = null) { onEvent(TalkEvent.Type) },
                    Alignment.Center,
                ) { ModeText(stringResource(R.string.talk_type_instead), ModeTextStyle.CalloutEmphasis) }
            }
            TalkPhase.Greeting, TalkPhase.Listening -> Row(horizontalArrangement = Arrangement.spacedBy(ModeSize.talkDockGap), verticalAlignment = Alignment.Top) {
                ModeTalkSideButton(ModeIcon.Keyboard, stringResource(R.string.talk_type), { onEvent(TalkEvent.Type) })
                ModeTalkMicButton(state.phase == TalkPhase.Listening, stringResource(R.string.talk_done), { onEvent(TalkEvent.Done) })
                if (state.hasDone) ModeTalkSideButton(ModeIcon.Checkmark, stringResource(R.string.talk_thats_all), { onEvent(TalkEvent.ThatsAll) })
                else ModeTalkSideButton(ModeIcon.Close, stringResource(R.string.talk_cancel), { onEvent(TalkEvent.Close) })
            }
            TalkPhase.Asking, TalkPhase.Answering -> if (typingAnswer) {
                ModeVoiceButton(stringResource(R.string.talk_send), ModeVoiceButtonStyle.Ink, { onEvent(TalkEvent.Send) }, height = ModeSize.primaryButton, enabled = canSend)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSize.talkDockGap), verticalAlignment = Alignment.Top) {
                    ModeTalkSideButton(ModeIcon.Keyboard, stringResource(R.string.talk_type), { onEvent(TalkEvent.Type) })
                    ModeTalkMicButton(state.phase == TalkPhase.Answering, stringResource(R.string.talk_done), { onEvent(TalkEvent.Done) })
                    ModeTalkSideButton(ModeIcon.ArrowRight, stringResource(R.string.talk_later), { onEvent(TalkEvent.Later) })
                }
            }
            TalkPhase.Starting -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
                // Why Mo can't hear right now, just above Stop.
                if (inConversation) ModeTalkMicOff(stringResource(R.string.talk_mic_off_note))
                ModeVoiceButton(stringResource(R.string.talk_stop), ModeVoiceButtonStyle.Danger, { onEvent(TalkEvent.Stop) }, Modifier.width(ModeSize.talkStopWidth), height = ModeSize.primaryButton)
            }
            TalkPhase.Answered -> Unit
            TalkPhase.Typing -> if (hearingFailed) {
                Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                    ModeVoiceButton(stringResource(R.string.talk_try_again), ModeVoiceButtonStyle.Ink, { onEvent(TalkEvent.TryAgain) })
                    ModeVoiceButton(stringResource(R.string.talk_type_instead), ModeVoiceButtonStyle.Raised, { onEvent(TalkEvent.Type) })
                }
            } else {
                ModeVoiceButton(stringResource(R.string.talk_send), ModeVoiceButtonStyle.Ink, { onEvent(TalkEvent.Send) }, height = ModeSize.primaryButton, enabled = canSend)
            }
        }
    }
}

private const val SETTLE_MS = 120L
private const val MS = 1000L
private const val MINUTE = 60L
private const val QUESTION_BARS = 26
