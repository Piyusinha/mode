package com.piyush.mode.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import com.piyush.mode.app.R
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.home.HomeRoute
import com.piyush.mode.app.home.WarningBannerHost
import com.piyush.mode.app.home.runningWords
import com.piyush.mode.app.run.RunStatus
import com.piyush.mode.app.run.rememberRunStatus
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.design.ModeEdgeFade
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeMiniPlayer
import com.piyush.mode.design.ModeMotion
import com.piyush.mode.design.ModeSetupHeader
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeTabBar
import com.piyush.mode.design.ModeTabItem
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle

/** What the main screens can open that sits over the tabs (the live run, Ask, talking mode). */
class Overlays(
    val openLive: () -> Unit,
    val openAsk: () -> Unit,
    val openProviders: () -> Unit = {},
    val reset: () -> Unit = {},
    val openTalk: (com.piyush.mode.app.talk.TalkRequest) -> Unit = {},
)

/**
 * The main screens: each tab's stack, and at the bottom the running task, the tabs and Speak. The bar
 * shows on each tab's first page; deeper pages have their own bottom (the conversation's message bar).
 */
@Composable
fun MainShell(graph: ModeGraph, shell: ShellViewModel, pages: SystemPages, signIn: com.piyush.mode.app.system.ChatGptSignIn, onReset: () -> Unit) {
    val nav = shell.navigator
    val run = rememberRunStatus(graph)
    var asking by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showingProviders by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val overlays = Overlays(
        openLive = { nav.push(Route.LiveRun) }, openAsk = { asking = true }, openProviders = { showingProviders = true }, reset = onReset,
        openTalk = nav::talk,
    )
    // A spoken question comes forward in talking mode: the open session's screen comes up to ask it, or, with no
    // session open, a screen of its own.
    val spokenQuestion = run.state.question?.takeIf { it.spoken }?.id
    LaunchedEffect(spokenQuestion) {
        // The overlay over another app asks it itself: Mode's own screen stays out of it.
        if (spokenQuestion != null && !com.piyush.mode.app.overlay.OverlayController.showing) {
            nav.talk(com.piyush.mode.app.talk.TalkRequest(forQuestion = true))
        }
    }
    // The open voice session (or a lone spoken question), kept here while its screen is down.
    val session = nav.talk
    // One voice session at a time: a session starting in Mode's app ends the overlay's.
    LaunchedEffect(session?.id) {
        if (session != null && !session.request.forQuestion && com.piyush.mode.app.overlay.OverlayController.showing) {
            com.piyush.mode.app.overlay.OverlayController.close("a voice session started in Mode")
        }
    }
    val talk = session?.let { com.piyush.mode.app.talk.rememberTalkPresenter(graph, shell.presenters, it) }
    if (talk != null) {
        // Over (End, ×, "that's all", paused too long, a question answered): the screen and the bar go.
        val finished by androidx.compose.runtime.produceState(false, talk) { talk.state.collect { value = it.finished } }
        LaunchedEffect(finished) { if (finished) nav.endTalk() }
        // Leaving Mode with the session open (Home, Back, another app, or Mode's own task opening one): the overlay
        // takes over this very session as Mo at the edge, and a tap on Mo opens it. Without phone control there's no
        // overlay: the mic pauses (Android gives it only to the app in front) and the session waits in Mode.
        val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
        androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
            if (session?.request?.forQuestion != false) return@LifecycleEventEffect
            nav.talkOpen = false
            if (com.piyush.mode.app.overlay.OverlayController.adopt(application, talk)) return@LifecycleEventEffect
            val s = talk.state.value
            val waiting = s.phase == com.piyush.mode.app.screens.settings.TalkPhase.Greeting || s.phase == com.piyush.mode.app.screens.settings.TalkPhase.Listening
            if (!s.sent && waiting) talk.pauseListening()
        }
        // Back in Mode: the session comes back from the overlay to its bar here.
        androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) {
            com.piyush.mode.app.overlay.OverlayController.giveBack(talk)
        }
    }

    LaunchedEffect(nav.pending) {
        val route = nav.pending ?: return@LaunchedEffect
        nav.pending = null
        nav.open(AppTab.Home, route)
    }
    BackHandler(enabled = nav.stack(nav.tab).isNotEmpty()) { nav.pop() }

    val stack = nav.stack(nav.tab)
    // The session bar, while a session is open and its screen is down. A chat shows it above its own message bar;
    // every other page makes room for it at the bottom (above the tabs on a tab's first page).
    val sessionBar: (@Composable () -> Unit)? = talk?.takeIf { session?.request?.forQuestion == false && !nav.talkOpen }?.let { presenter ->
        { com.piyush.mode.app.talk.TalkSessionBar(presenter, onOpen = { nav.talkOpen = true }) }
    }
    val inChat = stack.lastOrNull()?.route is Route.Conversation
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barHeight by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ModeSpacing.none) }
    val shellBar = sessionBar?.takeIf { !inChat }
    if (shellBar == null) barHeight = ModeSpacing.none
    Column(Modifier.fillMaxSize()) {
        // What stopped working since setup, over every tab; the page below makes room for it.
        WarningBannerHost(graph, pages, showing = stack.lastOrNull()?.route, navigate = { nav.push(it) })
        Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(com.piyush.mode.app.talk.LocalSessionBar provides sessionBar?.takeIf { inChat }) {
        AnimatedContent(
            targetState = nav.tab to stack.lastOrNull(),
            modifier = Modifier.padding(bottom = barHeight),
            transitionSpec = { fadeIn(tween(ModeMotion.quickMs)) togetherWith fadeOut(tween(ModeMotion.quickMs)) },
            label = "page",
        ) { (tab, entry) ->
            if (entry == null) {
                PresenterOwner(shell.presenters, "tab:$tab") { TabRoot(tab, graph, run, nav, overlays) }
            } else {
                PresenterOwner(shell.presenters, entry.id) { Page(entry.route, graph, run, nav, pages, overlays) }
            }
        }
        }
        if (asking) {
            com.piyush.mode.app.ask.AskRoute(graph, onSent = { conversation ->
                asking = false
                nav.open(AppTab.Home, Route.Conversation(conversation))
            }, onSpeak = {
                asking = false
                nav.talk(com.piyush.mode.app.talk.TalkRequest(entry = com.piyush.mode.app.talk.TalkRequest.ENTRY_ASK))
            }) { asking = false }
        }
        if (showingProviders) com.piyush.mode.app.settings.ProvidersRoute(graph, pages, signIn, { showingProviders = false })
        // Mode's question, wherever the user is (a spoken one is answered in talking mode).
        run.state.question?.takeIf { !it.spoken }?.let { question ->
            com.piyush.mode.app.run.QuestionSheet(question, run::answer, run::stopFromQuestion)
        }
        if (stack.isEmpty() || shellBar != null) {
            Box(Modifier.align(Alignment.BottomCenter), contentAlignment = Alignment.BottomCenter) {
                if (stack.isEmpty()) ModeEdgeFade(top = false)
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ModeSpacing.s)) {
                    if (shellBar != null) {
                        Box(
                            Modifier.padding(horizontal = ModeSpacing.m).padding(bottom = if (stack.isEmpty()) ModeSpacing.none else ModeSpacing.s)
                                .onSizeChanged { barHeight = with(density) { it.height.toDp() } + ModeSpacing.s },
                        ) { shellBar() }
                    }
                    if (stack.isEmpty()) MainBar(nav, run, overlays)
                }
            }
        }
        if (session != null && talk != null && (nav.talkOpen || session.request.forQuestion)) {
            com.piyush.mode.app.talk.TalkRoute(
                talk, session,
                openSettings = pages::appInfo,
                onVoiceSettings = { hearing -> nav.push(if (hearing) Route.Listening else Route.Voice) },
                onOpenChat = { nav.open(AppTab.Home, Route.Conversation(it)) },
                onWhatWentWrong = { nav.push(Route.TaskDebug(it)) },
                onMinimise = { nav.talkOpen = false },
                onClose = nav::endTalk,
            )
        }
        }
    }
}

@Composable
private fun MainBar(nav: Navigator, run: RunStatus, overlays: Overlays) {
    val running = run.state.running.takeIf { nav.tab != AppTab.Home }
    ModeTabBar(
        items = listOf(
            ModeTabItem(AppTab.Home, ModeIcon.Home, stringResource(R.string.tab_home)),
            ModeTabItem(AppTab.Tasks, ModeIcon.Tasks, stringResource(R.string.tab_tasks)),
            ModeTabItem(AppTab.Settings, ModeIcon.Settings, stringResource(R.string.tab_settings)),
        ),
        selection = nav.tab,
        // Settings opens its own page over the tab showing; back returns there.
        onSelect = { if (it == AppTab.Settings) nav.push(Route.Settings) else nav.select(it) },
        speakLabel = stringResource(R.string.tab_ask),
        onSpeak = overlays.openAsk,
        modifier = Modifier.padding(bottom = ModeSpacing.s),
    ) {
        if (running != null) {
            ModeMiniPlayer(
                running.title, running.status, running.paused, runningWords(),
                onPauseToggle = run::togglePause, onStop = run::stop, onOpen = overlays.openLive, compact = true,
            )
        }
    }
}

@Composable
private fun TabRoot(tab: AppTab, graph: ModeGraph, run: RunStatus, nav: Navigator, overlays: Overlays) {
    when (tab) {
        AppTab.Home -> HomeRoute(graph, run, nav::push, overlays.openLive)
        AppTab.Tasks -> com.piyush.mode.app.tasks.TasksRoute(graph, nav::push)
        else -> NotYet(nav)
    }
}

/** Every page a tab can open. */
@Composable
private fun Page(route: Route, graph: ModeGraph, run: RunStatus, nav: Navigator, pages: SystemPages, overlays: Overlays) {
    when (route) {
        is Route.Conversation -> com.piyush.mode.app.conversation.ConversationRoute(graph, route.id, nav::push, { nav.pop() }, overlays.openLive) {
            overlays.openTalk(com.piyush.mode.app.talk.TalkRequest(route.id, com.piyush.mode.app.talk.TalkRequest.ENTRY_CHAT))
        }
        Route.LiveRun -> com.piyush.mode.app.live.LiveRoute(graph) { nav.pop() }
        is Route.RunScreens -> com.piyush.mode.app.screensrun.RunScreensRoute(graph, route.taskId) { nav.pop() }
        is Route.Repeat -> com.piyush.mode.app.repeat.RepeatRoute(graph, route.text) { nav.pop() }
        Route.Settings -> com.piyush.mode.app.settings.SettingsRoute(graph, nav::push, { nav.pop() }, overlays.openProviders, overlays.reset)
        is Route.ProviderDetail -> com.piyush.mode.app.settings.ProviderDetailRoute(graph, route.id, pages, nav::push) { nav.pop() }
        is Route.PlanModels -> com.piyush.mode.app.settings.PlanModelsRoute(graph, route.brain) { nav.pop() }
        Route.Voice -> com.piyush.mode.app.settings.VoiceRoute(graph, hearing = false, pages, nav::push) { nav.pop() }
        Route.Listening -> com.piyush.mode.app.settings.VoiceRoute(graph, hearing = true, pages, nav::push) { nav.pop() }
        Route.Language -> com.piyush.mode.app.settings.LanguageRoute(graph) { nav.pop() }
        is Route.VoicePicker -> com.piyush.mode.app.settings.VoicePickerRoute(graph, route.provider) { nav.pop() }
        Route.Background -> com.piyush.mode.app.ModeApplication.backgroundSetup(androidx.compose.ui.platform.LocalContext.current)
            ?.let { com.piyush.mode.app.background.BackgroundRoute(it, pages) { nav.pop() } } ?: NotYet(nav)
        Route.Developer -> com.piyush.mode.app.settings.DeveloperRoute(graph) { nav.pop() }
        Route.Backup -> com.piyush.mode.app.settings.BackupRoute(graph) { nav.pop() }
        Route.Access -> com.piyush.mode.app.settings.AccessRoute(graph) { nav.pop() }
        Route.Health -> com.piyush.mode.app.settings.HealthRoute(graph, pages, nav::push) { nav.pop() }
        Route.Memory -> com.piyush.mode.app.memory.MemoryRoute(graph, com.piyush.mode.app.memory.MemoryPage.Hub, nav::push) { nav.pop() }
        Route.MemoryAboutYou -> com.piyush.mode.app.memory.MemoryRoute(graph, com.piyush.mode.app.memory.MemoryPage.AboutYou, nav::push) { nav.pop() }
        is Route.MemoryRoutines -> com.piyush.mode.app.memory.MemoryRoute(graph, com.piyush.mode.app.memory.MemoryPage.Routines(route.app), nav::push) { nav.pop() }
        Route.MemoryApps -> com.piyush.mode.app.memory.MemoryRoute(graph, com.piyush.mode.app.memory.MemoryPage.Apps, nav::push) { nav.pop() }
        is Route.MemoryApp -> com.piyush.mode.app.memory.MemoryRoute(graph, com.piyush.mode.app.memory.MemoryPage.App(route.name), nav::push) { nav.pop() }
        is Route.MemoryEdit -> com.piyush.mode.app.memory.MemoryRoute(graph, com.piyush.mode.app.memory.MemoryPage.Edit(route.id), nav::push) { nav.pop() }
        is Route.ModelPicker -> com.piyush.mode.app.settings.ModelPickerRoute(graph, route.stuck) { nav.pop() }
        Route.PhoneSetup -> com.piyush.mode.app.firstrun.PhoneSetupRoute(graph, pages, dots = null, onContinue = { nav.pop() }, onBack = { nav.pop() }) { nav.push(Route.Background) }
        is Route.TaskDebug -> com.piyush.mode.app.debug.TaskDebugRoute(graph, route.taskId, startAsking = false, nav::push) { nav.pop() }
        is Route.TaskDebugAsk -> com.piyush.mode.app.debug.TaskDebugRoute(graph, route.taskId, startAsking = true, nav::push) { nav.pop() }
        else -> NotYet(nav)
    }
}

/** A page still being built for Android: its back button and a line. */
@Composable
private fun NotYet(nav: Navigator) {
    Column(Modifier.fillMaxSize()) {
        ModeSetupHeader(stringResource(R.string.common_back), onBack = { nav.pop() })
        Box(Modifier.fillMaxWidth().padding(ModeSpacing.xxl), Alignment.Center) {
            ModeText(stringResource(R.string.common_coming_soon), ModeTextStyle.Title3)
        }
    }
}
