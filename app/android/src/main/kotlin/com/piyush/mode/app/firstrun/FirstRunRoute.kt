package com.piyush.mode.app.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.firstrun.FirstRunStep
import com.piyush.mode.app.screens.firstrun.PhoneSetupGuide
import com.piyush.mode.app.screens.firstrun.PhoneSetupKind
import com.piyush.mode.app.screens.firstrun.PhoneSetupPresenter
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.system.ChatGptSignIn
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.core.platform.PermissionStatus
import com.piyush.mode.design.ModeMotion

/**
 * The first run: Welcome, Connect your AI, Let Mode use your phone, a first job. Once the job is in
 * the queue (or skipped), [onFinished] gets its chat ("" for none) and Mode opens on it.
 */
@Composable
fun FirstRunRoute(
    graph: ModeGraph,
    pages: SystemPages,
    signIn: ChatGptSignIn,
    onMoreOptions: () -> Unit,
    onFinished: (conversation: String) -> Unit,
) {
    val presenter = rememberPresenter("firstRun") { graph.screens.firstRun() }
    val state by presenter.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { signIn.resumed() }
    LaunchedEffect(state.started) { state.started?.let(onFinished) }
    BackHandler(enabled = state.step != FirstRunStep.Welcome) { presenter.back() }

    fun dots(step: FirstRunStep) = StepDots(presenter.stepNumber(step), presenter.totalSteps)
    val onEvent: (FirstRunEvent) -> Unit = { event ->
        when (event) {
            FirstRunEvent.GetStarted -> presenter.getStarted()
            FirstRunEvent.SeeCode -> pages.web(REPOSITORY)
            FirstRunEvent.ConnectChatGpt -> signIn.start(
                presenter.loopbackPort, presenter::connectChatGpt,
                onAnswer = presenter::chatGptFinished, onCancelled = presenter::chatGptCancelled,
            )
            FirstRunEvent.CancelSignIn -> {
                signIn.stop()
                presenter.chatGptCancelled()
            }
            FirstRunEvent.MoreOptions -> onMoreOptions()
            FirstRunEvent.Next -> presenter.next()
            FirstRunEvent.Back -> presenter.back()
            is FirstRunEvent.StartJob -> presenter.startJob(event.text)
            FirstRunEvent.Skip -> {
                presenter.finish()
                onFinished("")
            }
        }
    }

    AnimatedContent(
        targetState = state.step,
        transitionSpec = { fadeIn(tween(ModeMotion.standardMs)) togetherWith fadeOut(tween(ModeMotion.quickMs)) },
        label = "firstRun",
    ) { step ->
        when (step) {
            FirstRunStep.Welcome -> WelcomeScreen(onEvent)
            FirstRunStep.Connect -> ConnectScreen(state.connect, state.connected, dots(step), onEvent)
            FirstRunStep.Phone -> PhoneSetupRoute(graph, pages, dots(step), onContinue = presenter::next, onBack = presenter::back)
            FirstRunStep.FirstTask -> FirstJobScreen(state.firstJobs, state.starting, dots(step), onEvent)
        }
    }
}

/** "Let Mode use your phone": in the first run ([dots]), and from Settings › Phone control. */
@Composable
fun PhoneSetupRoute(graph: ModeGraph, pages: SystemPages, dots: StepDots?, onContinue: () -> Unit, onBack: () -> Unit) {
    val presenter: PhoneSetupPresenter = rememberPresenter("phoneSetup") { graph.screens.phoneSetup() }
    val state by presenter.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { presenter.refresh() }
    BackHandler { if (state.guide != null) presenter.closeGuide() else onBack() }

    PhoneSetupScreen(state, dots) { event ->
        when (event) {
            is PhoneSetupEvent.Allow -> when {
                // Android stops asking after two refusals: its settings page is the only way left.
                event.row.kind == PhoneSetupKind.Notifications && event.row.status == PermissionStatus.Denied -> pages.notifications()
                else -> presenter.allow(event.row.kind)
            }
            is PhoneSetupEvent.Open -> when (event.guide) {
                PhoneSetupGuide.RestrictedSettings -> pages.appInfo()
                PhoneSetupGuide.PhoneControl -> pages.accessibility()
                PhoneSetupGuide.Assistant -> pages.defaultApps()
                PhoneSetupGuide.Battery -> pages.battery()
            }
            is PhoneSetupEvent.Alternative -> when (event.guide) {
                PhoneSetupGuide.RestrictedSettings -> presenter.unlocked()
                else -> presenter.closeGuide()
            }
            PhoneSetupEvent.CloseGuide -> presenter.closeGuide()
            PhoneSetupEvent.Continue -> onContinue()
            PhoneSetupEvent.Back -> onBack()
        }
    }
}

/** Mode's code (open source). */
private const val REPOSITORY = "https://github.com/Piyusinha/mode"
