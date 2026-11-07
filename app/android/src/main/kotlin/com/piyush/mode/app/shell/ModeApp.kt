package com.piyush.mode.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.piyush.mode.app.firstrun.FirstRunRoute
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.system.ChatGptSignIn
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeTheme
import com.piyush.mode.design.modeScreen

/** The whole app: the first run until setup is done, then the tabs. */
@Composable
fun ModeApp(graph: ModeGraph, shell: ShellViewModel, pages: SystemPages, signIn: ChatGptSignIn) {
    ModeTheme {
        var setup by rememberSaveable { mutableStateOf(graph.screens.needsOnboarding) }
        var providers by rememberSaveable { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Box(Modifier.fillMaxSize().modeScreen(Mode.colors).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (setup) {
                PresenterOwner(shell.presenters, FIRST_RUN) {
                    FirstRunRoute(
                        graph, pages, signIn,
                        onMoreOptions = { providers = true },
                        onFinished = { conversation ->
                            if (conversation.isNotEmpty()) shell.navigator.pending = Route.Conversation(conversation)
                            shell.presenters.release(FIRST_RUN)
                            setup = false
                        },
                    )
                    if (providers) com.piyush.mode.app.settings.ProvidersRoute(graph, pages, signIn, { providers = false }, setup = true)
                }
            } else {
                PresenterOwner(shell.presenters, "shell") {
                    MainShell(graph, shell, pages, signIn) {
                        // Reset Mode: wipe everything, then setup starts again.
                        scope.launch {
                            graph.resetEverything()
                            shell.presenters.releaseAll()
                            shell.navigator.reset()
                            setup = true
                        }
                    }
                }
            }
        }
    }
}

private const val FIRST_RUN = "firstRun"
