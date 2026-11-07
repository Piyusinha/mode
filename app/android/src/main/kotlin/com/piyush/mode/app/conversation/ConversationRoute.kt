package com.piyush.mode.app.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.conversation.ConversationPresenter
import com.piyush.mode.app.screens.conversation.ConversationUiState
import com.piyush.mode.app.screens.conversation.ReplyPart
import com.piyush.mode.app.screens.conversation.TurnItem
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.design.ModeActionSheet
import com.piyush.mode.design.ModeAgendaRow
import com.piyush.mode.design.ModeReplyBlock
import com.piyush.mode.design.ModeSheetAction
import com.piyush.mode.design.ModeTextSheet
import com.piyush.mode.design.MoMood
import com.piyush.mode.feature.tasks.domain.TaskSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What's open over the chat. */
private sealed interface Overlay {
    data class MessageMenu(val message: Message) : Overlay
    data object More : Overlay
    data object Add : Overlay
    data object ConfirmDelete : Overlay
    data class EditLesson(val lesson: LessonView) : Overlay
}

/** Connects a conversation to its presenter, its sheets, and Android's share and clipboard. */
@Composable
fun ConversationRoute(graph: ModeGraph, conversationId: String, navigate: (Route) -> Unit, close: () -> Unit, openLive: () -> Unit, talk: () -> Unit = {}) {
    val presenter: ConversationPresenter = rememberPresenter("conversation") { graph.screens.conversation(conversationId) }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val context = LocalContext.current
    val state = remember(ui) { map(ui, words) }
    var draft by rememberSaveable { mutableStateOf("") }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val thumbnails = remember { mutableStateMapOf<String, List<ImageBitmap>>() }

    // The first few screenshots of each task, for "See all N screens".
    LaunchedEffect(state.messages.mapNotNull { it.screens?.second }) {
        state.messages.mapNotNull { it.screens?.second }.filter { it !in thumbnails }.forEach { taskId ->
            thumbnails[taskId] = withContext(Dispatchers.IO) {
                val run = graph.screens.runScreens(taskId)
                try {
                    run.state.value.shots.take(3).mapNotNull { shot ->
                        run.image(shot)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                    }
                } finally {
                    run.close()
                }
            }
        }
    }

    ConversationScreen(state, thumbnails, draft, { draft = it }, draft.isNotBlank()) { event ->
        when (event) {
            ConversationEvent.Back -> close()
            ConversationEvent.Send -> if (presenter.send(draft)) draft = ""
            ConversationEvent.Speak -> talk()
            ConversationEvent.TogglePrivate -> presenter.setPrivate(!state.isPrivate)
            ConversationEvent.Add -> overlay = Overlay.Add
            ConversationEvent.More -> overlay = Overlay.More
            ConversationEvent.Share -> share(context, presenter.transcript())
            is ConversationEvent.Undo -> presenter.undoRemembered(event.id)
            is ConversationEvent.OpenScreens -> navigate(Route.RunScreens(event.taskId))
            ConversationEvent.Stop -> presenter.stop()
            ConversationEvent.OpenLive -> openLive()
            is ConversationEvent.MessageMenu -> overlay = Overlay.MessageMenu(event.message)
            is ConversationEvent.Retry -> presenter.retry(event.message.id)
            is ConversationEvent.Debug -> navigate(Route.TaskDebug(event.taskId))
            ConversationEvent.Remember -> presenter.remember()
            is ConversationEvent.UndoLesson -> presenter.undoLesson(event.id)
            is ConversationEvent.EditLesson -> state.lessons.firstOrNull { it.id == event.id }?.let { overlay = Overlay.EditLesson(it) }
            ConversationEvent.OpenVoiceSettings -> navigate(if (ui.voiceNotice?.hearing == true) Route.Listening else Route.Voice)
        }
    }

    val cancel = stringResource(R.string.common_cancel)
    when (val o = overlay) {
        is Overlay.MessageMenu -> ModeActionSheet(
            o.message.text.take(MENU_TITLE),
            listOf(
                ModeSheetAction(stringResource(R.string.conversation_copy)) { copy(context, o.message.text) },
                ModeSheetAction(stringResource(R.string.conversation_try_again)) { presenter.retry(o.message.id) },
                ModeSheetAction(stringResource(R.string.conversation_share_reply)) { share(context, o.message.text) },
            ),
            cancel,
        ) { overlay = null }
        Overlay.More -> ModeActionSheet(
            state.title,
            listOf(
                ModeSheetAction(stringResource(R.string.conversation_repeat)) { navigate(Route.Repeat(presenter.taskToRepeat())) },
                ModeSheetAction(stringResource(R.string.conversation_delete), destructive = true) { overlay = Overlay.ConfirmDelete },
            ),
            cancel,
        ) { if (overlay == Overlay.More) overlay = null }
        Overlay.Add -> ModeActionSheet(
            state.title,
            listOf(
                ModeSheetAction(stringResource(R.string.conversation_repeat)) { navigate(Route.Repeat(presenter.taskToRepeat())) },
                ModeSheetAction(stringResource(if (state.isPrivate) R.string.ask_private_on else R.string.ask_private_off)) { presenter.setPrivate(!state.isPrivate) },
            ),
            cancel,
        ) { overlay = null }
        Overlay.ConfirmDelete -> ModeActionSheet(
            stringResource(R.string.conversation_delete_confirm),
            listOf(ModeSheetAction(stringResource(R.string.conversation_delete), destructive = true) {
                presenter.delete()
                close()
            }),
            cancel,
        ) { overlay = null }
        is Overlay.EditLesson -> ModeTextSheet(
            stringResource(R.string.conversation_edit_lesson), o.lesson.text, stringResource(R.string.conversation_edit_lesson_prompt),
            stringResource(R.string.common_save), { presenter.editLesson(o.lesson.id, it) },
        ) { overlay = null }
        null -> Unit
    }
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, text))
}

private fun map(s: ConversationUiState, w: Words): ConversationState {
    val started = s.turns.firstOrNull()?.let { "${w.day(it.day)} ${w.timeOfDay(it.atMs)}" }.orEmpty()
    return ConversationState(
        title = s.title,
        whenLine = if (s.running != null && started.isNotEmpty()) w.get(R.string.conversation_when_working, started) else started,
        messages = s.turns.mapIndexed { i, t ->
            Message(
                id = t.id,
                fromUser = t.fromUser,
                spoken = t.source == TaskSource.Siri || t.source == TaskSource.Watch || t.source == TaskSource.Voice,
                steered = t.steered,
                text = t.text,
                blocks = t.parts.map { block(it, w) },
                screens = if (t.screens > 0) t.taskId?.let { w.get(R.string.conversation_see_screens, t.screens) to it } else null,
                debugTaskId = t.badge?.takeIf { it != TaskBadge.Done }?.let { t.taskId },
                mood = mood(t, s.turns.getOrNull(i - 1), s.running != null),
            )
        },
        working = s.running?.let { if (it.paused) w.get(R.string.run_paused) else it.status },
        workingAfterId = if (s.running == null) null else s.turns.lastOrNull { it.fromUser && !it.steered }?.id,
        workingSinceMs = s.running?.startedAtMs,
        remembered = s.remembered.map { RememberedView(it.itemId, w.get(R.string.conversation_remembered, it.text)) },
        isPrivate = s.privateNext,
        lessons = s.lessons.map { l ->
            LessonView(l.itemId, listOfNotNull(l.app, w.get(R.string.conversation_next_time_about, l.title)).joinToString(" · "), l.text, l.afterTurnId)
        },
        offerRemember = s.offerRemember != null,
        teaching = s.teaching,
        running = s.running != null,
        voiceNotice = s.voiceNotice?.let { n ->
            val provider = w.voiceProvider(n.provider)
            if (n.hearing) w.get(R.string.voice_couldnt_hear_with, provider) else w.get(R.string.voice_couldnt_speak_with, provider)
        },
    )
}

/** Mo's face for a reply: done, stuck, working while answering a steer, otherwise listening. */
private fun mood(t: TurnItem, previous: TurnItem?, running: Boolean): MoMood = when {
    t.badge != null -> if (t.badge == TaskBadge.Done) MoMood.Done else MoMood.Stuck
    running && previous?.steered == true -> MoMood.Working
    else -> MoMood.Idle
}

/** A shared reply part, as the design system draws it. */
fun block(p: ReplyPart, w: Words): ModeReplyBlock = when (p) {
    is ReplyPart.Paragraph -> ModeReplyBlock.Paragraph(p.text)
    is ReplyPart.Heading -> ModeReplyBlock.Heading(p.level, p.text)
    is ReplyPart.Bullets -> ModeReplyBlock.Bullets(p.items)
    is ReplyPart.Numbered -> ModeReplyBlock.Numbered(p.start, p.items)
    is ReplyPart.Agenda -> ModeReplyBlock.Agenda(p.rows.map { ModeAgendaRow(it.start ?: w.get(R.string.conversation_all_day), it.end, it.text, it.start == null) })
    is ReplyPart.Table -> ModeReplyBlock.Table(p.header, p.rows)
    is ReplyPart.Quote -> ModeReplyBlock.Quote(p.text)
    is ReplyPart.Code -> ModeReplyBlock.Code(p.text)
    ReplyPart.Divider -> ModeReplyBlock.Divider
}

/** A long reply's first words, as the title of its menu. */
private const val MENU_TITLE = 80
