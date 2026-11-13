package com.piyush.mode.app.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import com.piyush.mode.app.R
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeChatActions
import com.piyush.mode.design.ModeChatLessonCard
import com.piyush.mode.design.ModeChatNotice
import com.piyush.mode.design.ModeChatTitle
import com.piyush.mode.design.ModeFailedCard
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeMessageBar
import com.piyush.mode.design.ModeMessageBarWords
import com.piyush.mode.design.ModeMoReply
import com.piyush.mode.design.ModeOfferChip
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModeRememberedChip
import com.piyush.mode.design.ModeReply
import com.piyush.mode.design.ModeReplyBlock
import com.piyush.mode.design.ModeScreensLink
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSpinner
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeUserBubble
import com.piyush.mode.design.ModeWorkingCard
import com.piyush.mode.design.ModeWorkingTag
import com.piyush.mode.design.MoMood

/** One message: yours in a bubble; Mode's beside Mo, its first block there and the rest under it. */
data class Message(
    val id: String,
    val fromUser: Boolean,
    val spoken: Boolean,
    /** Sent while Mode worked, to change the running task. */
    val steered: Boolean,
    /** The raw text, for Copy. */
    val text: String,
    val blocks: List<ModeReplyBlock>,
    /** "See all 11 screens" and the task it opens. */
    val screens: Pair<String, String>?,
    /** A task that couldn't finish: What went wrong opens its check. */
    val debugTaskId: String?,
    val mood: MoMood,
) {
    val lead: List<ModeReplyBlock> get() = blocks.take(1)
    val rest: List<ModeReplyBlock> get() = blocks.drop(1)
}

data class LessonView(val id: String, val context: String, val text: String, val afterTurnId: String)
data class RememberedView(val id: String, val text: String)

data class ConversationState(
    val title: String = "",
    val whenLine: String = "",
    val messages: List<Message> = emptyList(),
    /** What Mode is doing now while this chat's task runs, and since when. */
    val working: String? = null,
    /** The request the running task came from: its working card sits under it. */
    val workingAfterId: String? = null,
    val workingSinceMs: Long? = null,
    val remembered: List<RememberedView> = emptyList(),
    val isPrivate: Boolean = false,
    val lessons: List<LessonView> = emptyList(),
    val offerRemember: Boolean = false,
    val teaching: Boolean = false,
    val running: Boolean = false,
    val voiceNotice: String? = null,
)

sealed interface ConversationEvent {
    data object Back : ConversationEvent
    data object Send : ConversationEvent
    data object Speak : ConversationEvent
    data object TogglePrivate : ConversationEvent
    data object Add : ConversationEvent
    data object More : ConversationEvent
    data object Share : ConversationEvent
    data class Undo(val id: String) : ConversationEvent
    data class OpenScreens(val taskId: String) : ConversationEvent
    data object Stop : ConversationEvent
    data object OpenLive : ConversationEvent
    data class MessageMenu(val message: Message) : ConversationEvent
    data class Retry(val message: Message) : ConversationEvent
    data class Debug(val taskId: String) : ConversationEvent
    data object Remember : ConversationEvent
    data class UndoLesson(val id: String) : ConversationEvent
    data class EditLesson(val id: String) : ConversationEvent
    data object OpenVoiceSettings : ConversationEvent
}

/** A conversation, as on the Chat board. */
@Composable
fun ConversationScreen(
    state: ConversationState,
    thumbnails: Map<String, List<ImageBitmap>>,
    draft: String,
    onDraft: (String) -> Unit,
    canSend: Boolean,
    onEvent: (ConversationEvent) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        ModePageBar(stringResource(R.string.common_back), { onEvent(ConversationEvent.Back) }) {
            ModeChatActions(
                stringResource(R.string.conversation_share), stringResource(R.string.conversation_more),
                { onEvent(ConversationEvent.Share) }, { onEvent(ConversationEvent.More) },
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(ModeSpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.chatGap),
        ) {
            item { ModeChatTitle(state.title, state.whenLine, Modifier.padding(bottom = ModeSpacing.l)) }
            items(state.messages, key = { it.id }) { message ->
                Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.chatGap)) {
                    MessageView(message, message.screens?.second?.let { thumbnails[it] }.orEmpty(), onEvent)
                    if (state.working != null && state.workingAfterId == message.id) Working(state, onEvent)
                    state.lessons.filter { it.afterTurnId == message.id }.forEach { lesson ->
                        ModeChatLessonCard(
                            stringResource(R.string.conversation_saved_for_next_time), lesson.context, lesson.text,
                            stringResource(R.string.conversation_edit), stringResource(R.string.common_undo),
                            { onEvent(ConversationEvent.EditLesson(lesson.id)) }, { onEvent(ConversationEvent.UndoLesson(lesson.id)) },
                        )
                    }
                }
            }
            if (state.working != null && state.workingAfterId == null) item { Working(state, onEvent) }
            state.voiceNotice?.let { notice ->
                item { ModeChatNotice(notice, stringResource(R.string.voice_update_in_settings), { onEvent(ConversationEvent.OpenVoiceSettings) }) }
            }
            if (state.offerRemember) item {
                ModeOfferChip(stringResource(R.string.conversation_also_next_time), stringResource(R.string.conversation_remember), { onEvent(ConversationEvent.Remember) })
            }
            if (state.teaching) item {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                    ModeSpinner()
                    ModeText(stringResource(R.string.conversation_saving), ModeTextStyle.Subheadline, color = Mode.colors.textSecondary)
                }
            }
            items(state.remembered, key = { "remembered-" + it.id }) { item ->
                ModeRememberedChip(item.text, stringResource(R.string.common_undo), { onEvent(ConversationEvent.Undo(item.id)) })
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.s),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // An open voice session, minimised: above the message bar.
            com.piyush.mode.app.talk.LocalSessionBar.current?.invoke()
            if (state.running) ModeWorkingTag(stringResource(R.string.conversation_mode_is_working))
            ModeMessageBar(
                draft, onDraft, state.isPrivate, canSend,
                ModeMessageBarWords(
                    stringResource(if (state.running) R.string.conversation_steer_prompt else R.string.conversation_teach_prompt),
                    stringResource(R.string.ask_add), stringResource(R.string.ask_send), stringResource(R.string.tab_ask),
                    stringResource(R.string.ask_private_on), stringResource(R.string.ask_private_off),
                ),
                onTogglePrivate = { onEvent(ConversationEvent.TogglePrivate) }, onSend = { onEvent(ConversationEvent.Send) },
                onSpeak = { onEvent(ConversationEvent.Speak) }, onAdd = { onEvent(ConversationEvent.Add) }, steering = state.running,
            )
        }
    }
}

@Composable
private fun Working(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    ModeWorkingCard(
        stringResource(R.string.conversation_working_title), state.working.orEmpty(), state.workingSinceMs, stringResource(R.string.common_stop),
        { onEvent(ConversationEvent.OpenLive) }, { onEvent(ConversationEvent.Stop) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageView(message: Message, thumbnails: List<ImageBitmap>, onEvent: (ConversationEvent) -> Unit) {
    if (message.fromUser) {
        when {
            message.steered -> ModeUserBubble(message.text, tag = stringResource(R.string.conversation_while_working), icon = ModeIcon.ArrowRight)
            message.spoken -> ModeUserBubble(message.text, tag = stringResource(R.string.conversation_spoken), icon = ModeIcon.Microphone)
            else -> ModeUserBubble(message.text, tag = stringResource(R.string.conversation_typed), icon = ModeIcon.Keyboard)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ModeSpacing.chatGap)) {
            // Copy, Try again and Share are on a long press, as on the iPhone.
            Box(Modifier.combinedClickable(onClick = {}, onLongClick = { onEvent(ConversationEvent.MessageMenu(message)) }, indication = null, interactionSource = null)) {
                ModeMoReply(message.mood) { ModeReply(message.lead) }
            }
            message.screens?.let { (label, taskId) -> ModeScreensLink(label, thumbnails, { onEvent(ConversationEvent.OpenScreens(taskId)) }) }
            if (message.rest.isNotEmpty()) ModeReply(message.rest)
            message.debugTaskId?.let { taskId ->
                ModeFailedCard(
                    stringResource(R.string.debug_couldnt_finish), stringResource(R.string.debug_card_detail),
                    stringResource(R.string.debug_what_went_wrong), stringResource(R.string.conversation_try_again),
                    { onEvent(ConversationEvent.Debug(taskId)) }, { onEvent(ConversationEvent.Retry(message)) },
                )
            }
        }
    }
}
