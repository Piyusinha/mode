package com.piyush.mode.app.screens.firstrun

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.settings.ChatGptPhase
import com.piyush.mode.app.screens.settings.ConnectChatGptPresenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.domain.TaskInbox
import com.piyush.mode.feature.tasks.domain.TaskSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The first run: Welcome, Connect your AI, then a real task. On Android, [Phone] comes before the task:
 * phone control is a switch the user turns on in Settings, so it is set up rather than asked for.
 */
public enum class FirstRunStep { Welcome, Connect, Phone, FirstTask }

/** Where connecting the AI is. */
public sealed interface ConnectState {
    /** ChatGPT recommended, Connect. */
    public data object Choose : ConnectState

    /** The ChatGPT page is open. */
    public data object Waiting : ConnectState

    /** Signed in; proving the plan works. */
    public data object Checking : ConnectState

    public data object Connected : ConnectState

    /** [notEligible]: the plan can't be used here (Free): use OpenRouter instead. */
    public data class Failed(val message: String, val notEligible: Boolean) : ConnectState
}

public data class FirstRunUiState(
    val step: FirstRunStep = FirstRunStep.Welcome,
    val connect: ConnectState = ConnectState.Choose,
    /** Some AI is connected (ChatGPT, Claude or OpenRouter): Continue works. */
    val connected: Boolean = false,
    /** Small, reliable first jobs. */
    val firstJobs: List<String> = FIRST_JOBS,
    /** The first job was picked: asking for what it needs (the VPN, notifications), then it starts. */
    val starting: Boolean = false,
    /**
     * The first job is in the queue and setup is over: the app opens its chat (or Home when it has none,
     * an empty string). Null until then.
     */
    val started: String? = null,
) {
    public companion object {
        /** Work on any iPhone, change nothing anyone else sees, and are easy to undo. */
        public val FIRST_JOBS: List<String> = listOf("Turn on Do Not Disturb", "Turn off Wi-Fi", "Set an alarm for 7 am")
    }
}

/**
 * The first run: Welcome, Connect your AI, then one real task within a few minutes. Name, models, voice
 * and dictation are set up later (after the first task), not here. Permissions are asked when the first
 * job starts ([prepare]: the VPN phone control needs, and notifications for its questions).
 */
public class FirstRunPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val chatGpt: ConnectChatGptPresenter,
    /** Where the first job goes: the same queue as every task. */
    private val inbox: TaskInbox? = null,
    /** Asks iOS for what the first task needs (the VPN, notifications); a refusal doesn't stop the task. */
    private val prepare: suspend () -> Unit = {},
    /** Phone control has its own setup step (Android), between connecting the AI and the first job. */
    private val phoneStep: Boolean = false,
) : Presenter<FirstRunUiState>(parent, FirstRunUiState(connected = settings.settings.value.hasModelAccess)) {
    private val step = MutableStateFlow(FirstRunStep.Welcome)
    private val starting = MutableStateFlow(false)
    private val started = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            combine(step, chatGpt.state, settings.settings, starting, started) { step, c, s, isStarting, startedIn ->
                val connected = s.hasModelAccess
                FirstRunUiState(
                    step = step,
                    connect = when (val p = c.phase) {
                        ChatGptPhase.Waiting -> ConnectState.Waiting
                        ChatGptPhase.Checking -> ConnectState.Checking
                        is ChatGptPhase.Failed -> ConnectState.Failed(p.message, p.notEligible)
                        is ChatGptPhase.Connected -> ConnectState.Connected
                        ChatGptPhase.Idle -> if (connected) ConnectState.Connected else ConnectState.Choose
                    },
                    connected = connected,
                    starting = isStarting,
                    started = startedIn,
                )
            }.collect { mutableState.value = it }
        }
    }

    public fun getStarted() { step.value = FirstRunStep.Connect }

    /** The loopback port the ChatGPT page answers on. */
    public val loopbackPort: Int get() = chatGpt.loopbackPort

    /** The ChatGPT page to open (the sign-in comes back to [chatGptFinished]). */
    public fun connectChatGpt(): String = chatGpt.start()

    public fun chatGptFinished(callbackUrl: String) { chatGpt.finish(callbackUrl) }

    /** The page was closed without signing in (Cancel). */
    public fun chatGptCancelled() { chatGpt.cancelled() }

    /** Continue: only once an AI is connected. */
    public fun next() {
        if (!state.value.connected) return
        when (step.value) {
            FirstRunStep.Connect -> if (phoneStep) {
                AppLog.log("firstrun", "connected: on to phone control")
                step.value = FirstRunStep.Phone
            } else {
                AppLog.log("firstrun", "connected: on to the first job")
                step.value = FirstRunStep.FirstTask
            }
            FirstRunStep.Phone -> {
                AppLog.log("firstrun", "phone set up: on to the first job")
                step.value = FirstRunStep.FirstTask
            }
            else -> Unit
        }
    }

    public fun back() {
        step.value = when (step.value) {
            FirstRunStep.FirstTask -> if (phoneStep) FirstRunStep.Phone else FirstRunStep.Connect
            FirstRunStep.Phone -> FirstRunStep.Connect
            else -> FirstRunStep.Welcome
        }
    }

    /** How many steps the dots count, Welcome included: 3 on the iPhone, 4 with phone control. */
    public val totalSteps: Int get() = if (phoneStep) 4 else 3

    /** Which dot [step] is, counting from 1 (Welcome). */
    public fun stepNumber(step: FirstRunStep): Int = when (step) {
        FirstRunStep.Welcome -> 1
        FirstRunStep.Connect -> 2
        FirstRunStep.Phone -> 3
        FirstRunStep.FirstTask -> if (phoneStep) 4 else 3
    }

    /**
     * A first job was picked (or typed): ask for what it needs, put it in the queue, and finish setup so
     * Mode opens on its chat, where it runs like any task.
     */
    public fun startJob(text: String) {
        val job = text.trim()
        if (job.isEmpty() || !state.value.connected || starting.value || started.value != null) return
        starting.value = true
        scope.launch {
            try {
                prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.log("firstrun", "permissions: ${e.message}")
            }
            val conversation = inbox?.submit(job, TaskSource.App, null, false)?.conversationId
            AppLog.log("firstrun", "first job started")
            settings.update { it.copy(onboardingDone = true) }
            started.value = conversation.orEmpty()
            starting.value = false
        }
    }

    /** Skipped the first job: Mode opens on Home. */
    public fun finish() {
        AppLog.log("firstrun", "finished")
        settings.update { it.copy(onboardingDone = true) }
    }
}
