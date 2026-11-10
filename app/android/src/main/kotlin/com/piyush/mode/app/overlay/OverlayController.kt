package com.piyush.mode.app.overlay

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.android.OverlayGuard
import com.piyush.mode.android.ServiceRegistry
import com.piyush.mode.app.MainActivity
import com.piyush.mode.app.ModeApplication
import com.piyush.mode.app.background.UserTakeover
import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.screens.run.ActivityUiState
import com.piyush.mode.app.screens.run.LiveActivityPresenter
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.app.screens.settings.ListenPresenter
import com.piyush.mode.app.screens.settings.OVERLAY_ENTRY
import com.piyush.mode.app.shell.DeepLink
import com.piyush.mode.app.voice.VoiceLevel
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.QuestionActions
import com.piyush.mode.core.platform.QuestionKind
import com.piyush.mode.design.ModeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The agent overlay: Mode over whatever app the user is in, from "Ask Mode", the widget or the tile. One session at a
 * time: a hands-free voice conversation (ListenPresenter, entry "overlay"), the running task's progress, its
 * questions and its result, without Mode's app ever coming forward. Main thread.
 */
object OverlayController {
    private val scope = MainScope()
    private var app: Application? = null
    private var host: OverlayHost? = null
    private var session: ListenPresenter? = null
    private var activity: LiveActivityPresenter? = null
    private var live: LiveRunPresenter? = null
    private var home: HomePresenter? = null
    private var watch: Job? = null
    private var slimming: Job? = null
    private var closing: Job? = null
    /** The conversation was started here (not a task already running when the overlay was summoned). */
    private var begun = false
    /** A task was seen working while this overlay showed (so its end can close an overlay summoned mid-task). */
    private var sawWork = false
    private val local = MutableStateFlow(OverlayLocal())
    private val state = MutableStateFlow<OverlayView?>(null)

    /** What the overlay shows now (null: not showing). */
    internal val view: StateFlow<OverlayView?> = state.asStateFlow()

    val showing: Boolean get() = host != null

    /**
     * Shows Mode over the user's app: greets and listens, or shows the task already running, or starts [task].
     * False when it can't (phone control is off): the caller opens Mode's app instead.
     */
    fun summon(app: Application, task: String? = null, entry: String = "ask"): Boolean {
        val service = ServiceRegistry.service.value ?: return false
        this.app = app
        val graph = ModeApplication.graph(app)
        // One voice session at a time: a session left open in Mode's app ends when the overlay starts its own.
        if (host == null) endAppSession?.invoke()
        if (host == null) open(service, graph)
        AppLog.log("overlay", "summoned from $entry")
        closing?.cancel()
        local.update { it.copy(slim = false, typing = false, minimized = false) }
        when {
            task != null -> { begun = true; session?.submitTyped(task) }
            !begun && !graph.tasks.state.value.running -> { begun = true; session?.begin() }
        }
        return true
    }

    /** What the overlay shows, for the debug probe (no screenshots in it). */
    fun describe(): String = when (val v = state.value) {
        null -> "closed"
        is OverlayView.Working -> "Working(task=${v.task}, now=${v.now}, steps=${v.steps.map { it.text }}, ${v.done}/${v.parts}, slim=${v.slim}, onScreen=${v.onScreen}, paused=${v.paused}, screen=${v.screen?.size})"
        else -> v.toString()
    }

    /** Words for the overlay's conversation as if said (the debug probe; the phone hears them otherwise). */
    fun say(text: String) {
        session?.pick(text)
    }

    fun close(why: String) {
        if (host == null) return
        val pending = activity?.state?.value as? ActivityUiState.Waiting
        watch?.cancel()
        slimming?.cancel()
        closing?.cancel()
        session?.dismiss()
        listOfNotNull(session, activity, live, home).forEach { it.close() }
        session = null; activity = null; live = null; home = null
        host?.close()
        host = null
        OverlayPresence.showing = false
        OverlayGuard.hide = null
        OverlayGuard.show = null
        OverlayGuard.passThrough = null
        begun = false
        sawWork = false
        local.value = OverlayLocal()
        state.value = null
        // A question still open goes back to a notification: the overlay was the one asking it.
        val a = app
        if (pending != null && a != null) {
            val kind = when {
                pending.yourTurn -> QuestionKind.YourTurn
                pending.options.isNotEmpty() -> QuestionKind.Choice
                pending.yesNo -> QuestionKind.YesNo
                else -> QuestionKind.Text
            }
            ModeApplication.graph(a).notifier.ask(pending.questionId, pending.question, kind, pending.options)
        }
        AppLog.log("overlay", "closed: $why")
    }

    private fun open(service: android.accessibilityservice.AccessibilityService, graph: com.piyush.mode.app.graph.ModeGraph) {
        val h = OverlayHost(service)
        host = h
        val talk = graph.screens.listen(null, OVERLAY_ENTRY).also { session = it }
        val act = graph.screens.liveActivity().also { activity = it }
        val run = graph.screens.liveRun().also { live = it }
        val suggestions = graph.screens.home().also { home = it }
        OverlayPresence.showing = true
        // On the user's own screen the overlay steps aside for Mode's look and touch.
        OverlayGuard.hide = { withContext(Dispatchers.Main) { host?.setVisible(false); OverlayHost.nextFrames() } }
        OverlayGuard.show = { scope.launch { host?.setVisible(true) } }
        OverlayGuard.passThrough = { on -> withContext(Dispatchers.Main) { host?.setPassThrough(on) } }
        watch = scope.launch {
            launch {
                val withTakeover = combine(local, UserTakeover.active) { loc, theirs -> loc.copy(takenOver = theirs) }
                combine(talk.state, act.state, run.state, withTakeover, suggestions.state) { s, a, l, loc, homeState ->
                    if (a is ActivityUiState.Working) sawWork = true
                    val chips = (homeState.suggestions.map { it.text } + homeState.tryThese).distinct().take(CHIPS)
                    overlayView(s, a, l, loc, onHiddenScreen = com.piyush.mode.app.graph.background?.working == true, chips) to (s.finished to a)
                }.collect { (v, end) ->
                    val (finished, a) = end
                    state.value = v
                    // Listening again: the next quiet spell may shrink it again.
                    if (!talk.state.value.micPaused && local.value.shrankWhenQuiet) local.update { it.copy(shrankWhenQuiet = false) }
                    layout(v)
                    autoSlim(v)
                    when {
                        finished -> closeSoon("the conversation ended")
                        // Quiet after a task: the overlay shrinks to Mo at the edge (mic paused), out of the way but
                        // still there. It goes only when the conversation ends.
                        v !is OverlayView.Working && v !is OverlayView.Minimized && talk.state.value.micPaused &&
                            talk.state.value.doneSoFar.isNotEmpty() && a !is ActivityUiState.Working && a !is ActivityUiState.Waiting &&
                            !local.value.minimized && !local.value.shrankWhenQuiet -> {
                            local.update { it.copy(minimized = true, shrankWhenQuiet = true) }
                        }
                        !begun && sawWork && a !is ActivityUiState.Working && a !is ActivityUiState.Waiting -> closeSoon("the task ended")
                    }
                }
            }
            // Phone control went off: the overlay can't stay (the task carries on in the notification).
            launch { ServiceRegistry.service.collect { if (it == null) close("phone control off") } }
        }
    }

    private fun closeSoon(why: String) {
        if (closing?.isActive == true) return
        closing = scope.launch {
            delay(CLOSE_AFTER_MS)
            close(why)
        }
    }

    /** While Mode works on the hidden screen, the sheet shrinks to the slim bar after a moment. */
    private fun autoSlim(v: OverlayView) {
        val working = v is OverlayView.Working && !v.onScreen && !v.slim && !v.paused
        if (!working) {
            slimming?.cancel()
            if (v !is OverlayView.Working && local.value.slim) local.update { it.copy(slim = false) }
            return
        }
        if (slimming?.isActive == true) return
        slimming = scope.launch {
            delay(SLIM_AFTER_MS)
            local.update { it.copy(slim = true) }
        }
    }

    /** The sheet (or bar), or on the user's own screen the Mo button and the glow. */
    private fun layout(v: OverlayView) {
        val h = host ?: return
        if (v is OverlayView.Minimized) {
            h.hide(OverlayWindow.Sheet)
            h.hide(OverlayWindow.Glow)
            val y = ((app?.resources?.displayMetrics?.heightPixels ?: 0) * BUTTON_HEIGHT).toInt()
            h.show(OverlayWindow.Button, WindowSpec(Gravity.END or Gravity.TOP, y = y)) { Themed { ButtonRoute() } }
        } else if (v is OverlayView.OnScreen) {
            h.hide(OverlayWindow.Sheet)
            h.show(OverlayWindow.Glow, WindowSpec(Gravity.FILL, fullScreen = true, touchable = false)) { Themed { OverlayGlow() } }
            val y = ((app?.resources?.displayMetrics?.heightPixels ?: 0) * BUTTON_HEIGHT).toInt()
            h.show(OverlayWindow.Button, WindowSpec(Gravity.END or Gravity.TOP, y = y)) { Themed { ButtonRoute() } }
        } else {
            h.hide(OverlayWindow.Button)
            h.hide(OverlayWindow.Glow)
            h.show(OverlayWindow.Sheet, WindowSpec(Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, focusable = v is OverlayView.Typing)) { Themed { SheetRoute() } }
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        ModeTheme { content() }
    }

    @Composable
    private fun SheetRoute() {
        val v by view.collectAsStateWithLifecycle()
        val level by VoiceLevel.level.collectAsStateWithLifecycle()
        val speaking by VoiceLevel.speaking.collectAsStateWithLifecycle()
        val current = v ?: return
        val jpeg = (current as? OverlayView.Working)?.screen
        val screen = remember(jpeg) { jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        OverlaySheet(current, screen, level, speaking, ::onEvent)
    }

    @Composable
    private fun ButtonRoute() {
        val v by view.collectAsStateWithLifecycle()
        when (val current = v) {
            is OverlayView.OnScreen -> OverlayButton(current.bubble, ::onEvent)
            is OverlayView.Minimized -> OverlayMinimized(current, ::onEvent)
            else -> Unit
        }
    }

    internal fun onEvent(event: OverlayEvent) {
        val talk = session
        when (event) {
            OverlayEvent.Close -> close("closed by the user")
            // ⌄: Mo goes to the edge; nothing stops (only × ends it).
            OverlayEvent.Slim -> { slimming?.cancel(); local.update { it.copy(minimized = true, typing = false) } }
            OverlayEvent.Expand -> { slimming?.cancel(); local.update { it.copy(slim = false, minimized = false) } }
            OverlayEvent.Listen -> talk?.let { t ->
                val s = t.state.value
                when {
                    s.micPaused -> t.resumeListening()
                    s.turns.isEmpty() -> t.begin()
                    else -> t.listenAgain()
                }
            }
            is OverlayEvent.Say -> talk?.pick(event.text)
            is OverlayEvent.Send -> {
                local.update { it.copy(typing = false) }
                val question = event.questionId
                if (question != null) activity?.answer(question, event.text) else talk?.submitTyped(event.text)
            }
            OverlayEvent.TypeInstead -> {
                if (state.value is OverlayView.Question) talk?.later() else talk?.typeInstead()
                local.update { it.copy(typing = true) }
            }
            is OverlayEvent.Answer -> activity?.answer(event.questionId, event.text)
            is OverlayEvent.EnteredIt -> activity?.answer(event.questionId, QuestionActions.ANSWER_DONE)
            OverlayEvent.TakeOver -> scope.launch { UserTakeover.takeOver() }
            OverlayEvent.GiveBack -> scope.launch { UserTakeover.giveBack() }
            OverlayEvent.Stop -> activity?.stop()
            OverlayEvent.Pause -> { live?.pause(); local.update { it.copy(paused = true) } }
            OverlayEvent.Resume -> { live?.resume(); local.update { it.copy(paused = false) } }
            is OverlayEvent.OpenChat -> {
                app?.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("${DeepLink.SCHEME}://conversation/${event.conversationId}"), app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                close("opened the chat")
            }
            // Android asks for the microphone from an app screen, not over another app: the conversation moves to
            // Mode's own voice screen, which asks and carries on. One session, not the overlay's and the app's.
            OverlayEvent.AllowMic -> openEntry?.invoke() ?: run {
                close("the microphone is asked for in Mode")
                app?.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("${DeepLink.SCHEME}://listen"), app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
    }

    /** Ends the voice session open in Mode's own app, if any (two sessions would both listen). Set by the app. */
    var endAppSession: (() -> Unit)? = null

    /** Opens the small entry screen (it asks for the microphone, which an overlay can't). Set by the app. */
    var openEntry: (() -> Unit)? = null

    private const val CHIPS = 2
    private const val SLIM_AFTER_MS = 4_000L
    private const val CLOSE_AFTER_MS = 2_000L
    /** The Mo button sits a little below the middle of the screen's right edge. */
    private const val BUTTON_HEIGHT = 0.55
}
