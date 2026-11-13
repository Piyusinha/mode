package com.piyush.mode.app.overlay

import com.piyush.mode.app.screens.run.ActivityUiState
import com.piyush.mode.app.screens.run.LiveRunUiState
import com.piyush.mode.app.screens.settings.ListenUiState
import com.piyush.mode.app.screens.settings.TalkOffer
import com.piyush.mode.app.screens.settings.TalkPhase
import com.piyush.mode.app.screens.settings.TalkResultRow
import com.piyush.mode.app.screens.settings.TalkTaskCard
import com.piyush.mode.app.screens.settings.TalkTurn
import com.piyush.mode.core.platform.ModePresence

/** An overlay over the user's app is showing Mode: the runner keeps Mode's own app back (and so do notifications). */
object OverlayPresence : ModePresence {
    @Volatile var showing: Boolean = false
    override val overlay: Boolean get() = showing
}

/** What only the overlay knows: shrunk to a bar, typing, the user took over, paused from the Mo button. */
internal data class OverlayLocal(
    val slim: Boolean = false,
    /** Shrunk to the Mo button at the screen's edge (⌄, or quiet after a task): the conversation carries on. */
    val minimized: Boolean = false,
    /** It already shrank by itself after going quiet (not again until the user brings it back and it goes quiet anew). */
    val shrankWhenQuiet: Boolean = false,
    val typing: Boolean = false,
    val takenOver: Boolean = false,
    val paused: Boolean = false,
)

/** One step for the live stream: what, how long into the task, and whether it's the one Mode is on. */
internal data class StreamStep(val text: String, val atSeconds: Long, val now: Boolean)

/** What the overlay shows, one state per board (O01–O10). */
internal sealed interface OverlayView {
    /** O01: Mode's greeting (or its next line), suggestions, and listening. */
    data class Greet(val line: String, val listening: Boolean, val chips: List<String>) : OverlayView

    /** O02: the words heard so far. */
    data class Heard(val words: String) : OverlayView

    /** O03 (full) and O04 (slim): Mode working, step by step; [onScreen] when it's on the user's own screen (paused). */
    data class Working(
        val task: String,
        val steps: List<StreamStep>,
        val screen: ByteArray?,
        val done: Int,
        val parts: Int,
        val now: String,
        val slim: Boolean,
        val onScreen: Boolean,
        val paused: Boolean,
    ) : OverlayView

    /** O05 / O10: Mode's question, its answers, and listening for the spoken one. */
    data class Question(
        val questionId: String,
        val task: String,
        val question: String,
        val options: List<String>,
        val yesNo: Boolean,
        val words: String,
        val listening: Boolean,
    ) : OverlayView

    /** O06: the user's part (a PIN): the app is theirs until they say they've done it. */
    data class Turn(val questionId: String, val what: String) : OverlayView

    /** The user took over the app Mode was using; they give it back when done. */
    data object InControl : OverlayView

    /** O07: how it went, then "Anything else?" while listening. */
    data class Result(
        val task: String,
        val headline: String,
        val rows: List<TalkResultRow>,
        val conversationId: String?,
        val next: String,
        val listening: Boolean,
    ) : OverlayView

    /** O08: why it didn't finish, the offer to try again, and listening. */
    data class Failed(val task: String, val line: String, val offer: TalkOffer?, val taskId: String?, val conversationId: String?, val listening: Boolean) :
        OverlayView

    /** Typing instead of talking. */
    data class Typing(val questionId: String?) : OverlayView

    /** Mode can't hear yet: the microphone isn't allowed. */
    data object NeedsMic : OverlayView

    /** O09: Mode is tapping the user's own screen: just the Mo button (with what it's doing) and the glow. */
    data class OnScreen(val bubble: String) : OverlayView

    /** Not listening (nothing was heard, or the mic was paused): a mic to tap and a keyboard, no keyboard opened. */
    data class Paused(val line: String, val chips: List<String>) : OverlayView

    /** Shrunk to the Mo button at the edge, saying what Mode is doing ([text]: what it's working on or saying). */
    data class Minimized(val kind: MinimizedKind, val text: String) : OverlayView
}

/** What the Mo button says while the overlay is shrunk. */
internal enum class MinimizedKind { Listening, Speaking, Working, Paused }

/**
 * What the overlay shows, from the voice session, the running task and the overlay's own state. The task wins over the
 * conversation (a question, the user's turn, work in progress); between tasks the conversation decides.
 */
internal fun overlayView(
    session: ListenUiState,
    activity: ActivityUiState,
    live: LiveRunUiState,
    local: OverlayLocal,
    onHiddenScreen: Boolean,
    chips: List<String>,
): OverlayView {
    val listening = session.phase == TalkPhase.Listening || session.phase == TalkPhase.Answering
    if (local.takenOver) return OverlayView.InControl
    if (local.typing) return OverlayView.Typing((activity as? ActivityUiState.Waiting)?.takeIf { !it.yourTurn }?.questionId)
    when (activity) {
        is ActivityUiState.Waiting ->
            return if (activity.yourTurn) OverlayView.Turn(activity.questionId, activity.question)
            else OverlayView.Question(
                activity.questionId, activity.task, activity.question, activity.options, activity.yesNo,
                words = if (session.phase == TalkPhase.Answering) session.partial else "",
                listening = session.phase == TalkPhase.Answering,
            )
        is ActivityUiState.Working -> {
            val onScreen = !onHiddenScreen
            if (onScreen && !local.paused && !activity.paused) {
                return OverlayView.OnScreen(activity.now)
            }
            if (local.minimized) return OverlayView.Minimized(MinimizedKind.Working, activity.now.ifBlank { activity.task })
            return OverlayView.Working(
                task = activity.task,
                steps = stream(live, activity),
                screen = live.screen.takeIf { onHiddenScreen },
                done = activity.plan.count { it.state == com.piyush.mode.app.screens.run.PlanStepState.Done },
                parts = activity.plan.size,
                now = activity.now,
                slim = local.slim && onHiddenScreen,
                onScreen = onScreen,
                paused = local.paused || activity.paused,
            )
        }
        else -> Unit
    }
    if (local.minimized) {
        val kind = when {
            session.micPaused || session.phase == TalkPhase.Typing -> MinimizedKind.Paused
            session.phase == TalkPhase.Starting -> MinimizedKind.Working
            session.phase == TalkPhase.Greeting -> MinimizedKind.Speaking
            else -> MinimizedKind.Listening
        }
        val text = when (kind) {
            MinimizedKind.Working -> session.working.ifBlank { session.task }
            MinimizedKind.Speaking -> session.turns.lastOrNull { !it.mine }?.text ?: session.greeting
            else -> ""
        }
        return OverlayView.Minimized(kind, text)
    }
    // Nothing heard, or the mic paused: Mode waits with a mic to tap. The keyboard opens only when they ask for it.
    if (session.micPaused && session.phase != TalkPhase.Starting) return OverlayView.Paused(session.greeting, chips)
    return when (session.phase) {
        TalkPhase.NeedsMic -> OverlayView.NeedsMic
        TalkPhase.Typing -> OverlayView.Paused(session.turns.lastOrNull { !it.mine }?.text ?: session.greeting, chips)
        TalkPhase.Starting -> OverlayView.Working(session.task, emptyList(), null, 0, 0, session.reply.ifBlank { session.working }, slim = false, onScreen = !onHiddenScreen, paused = false)
        else -> {
            // After a task: its card and Mode's line after it (the result and "Anything else?", or why it failed).
            val lastNext = session.turns.lastOrNull()?.takeIf { it.kind == TalkTurn.Kind.Next }
            val card = session.turns.lastOrNull { it.kind == TalkTurn.Kind.Task }?.card
            when {
                listening && session.partial.isNotBlank() && lastNext?.offer == null -> OverlayView.Heard(session.partial)
                lastNext != null && card != null && card.state == TalkTaskCard.State.Failed ->
                    OverlayView.Failed(card.title, lastNext.text, lastNext.offer, card.taskId, card.conversationId, listening)
                lastNext != null && card != null ->
                    OverlayView.Result(card.title, card.headline, card.rows, card.conversationId, lastNext.text, listening)
                else -> OverlayView.Greet(session.greeting, listening, chips)
            }
        }
    }
}

/** The steps so far with when each happened, and the one Mode is on now. */
private fun stream(live: LiveRunUiState, activity: ActivityUiState.Working): List<StreamStep> {
    var at = 0.0
    val done = live.steps.filter { it.reason.isNotBlank() }.map { step ->
        at += step.seconds
        StreamStep(step.reason, at.toLong(), now = false)
    }
    val since = activity.startedAtMs?.let { (System.currentTimeMillis() - it) / MS } ?: at.toLong()
    val now = StreamStep(activity.now, since, now = true).takeIf { activity.now.isNotBlank() && done.lastOrNull()?.text != activity.now }
    return (done.takeLast(SHOWN_DONE) + listOfNotNull(now))
}

private const val MS = 1000L
/** The stream shows the last few steps done and the one Mode is on. */
private const val SHOWN_DONE = 3
