package com.piyush.mode.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.piyush.mode.app.shell.AppTab
import com.piyush.mode.app.shell.DeepLink
import com.piyush.mode.app.shell.ModeApp
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.ShellViewModel
import com.piyush.mode.app.system.ChatGptSignIn
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.ActivityBridge
import kotlinx.coroutines.launch

/** The one activity; every screen is a Compose destination inside it. */
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ActivityBridge.attach(this)
        val graph = ModeApplication.graph(this)
        val pages = SystemPages(this)
        val signIn = ChatGptSignIn(this, lifecycleScope, pages)
        com.piyush.mode.app.overlay.OverlayController.endAppSession = { shell.navigator.endTalk() }
        // A link not yet followed (also when Android brings back a screen it had closed, with a new link).
        follow(intent)
        setContent {
            androidx.compose.foundation.layout.Box {
                ModeApp(graph, shell, pages, signIn)
                // In the small window, only the hidden screen and Mo (the app underneath keeps its place).
                if (inPip.value) com.piyush.mode.design.ModeTheme { com.piyush.mode.app.live.PipRoute(graph) }
            }
        }
        // While a task runs on the hidden screen, leaving Mode shrinks it to a small window.
        lifecycleScope.launch {
            (application as ModeApplication).backgroundTask.collect { running ->
                runCatching { setPictureInPictureParams(pipParams(running)) }
                // The task ended: the small window goes (its answer comes as a notification, and in the chat).
                if (!running && isInPictureInPictureMode) moveTaskToBack(true)
            }
        }
    }

    private val inPip = androidx.compose.runtime.mutableStateOf(false)

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    /** Tall like the board's small window, with Stop. */
    private fun pipParams(autoEnter: Boolean): android.app.PictureInPictureParams {
        val stop = android.app.PendingIntent.getBroadcast(
            this, 0, Intent(this, StopReceiver::class.java), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = getString(R.string.working_stop)
        val action = android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(this, com.piyush.mode.design.ModeIcon.Stop.res), label, label, stop)
        return android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(PIP_WIDTH, PIP_HEIGHT))
            .setAutoEnterEnabled(autoEnter)
            .setSeamlessResizeEnabled(true)
            .setActions(listOf(action))
            .build()
    }

    override fun onDestroy() {
        com.piyush.mode.app.overlay.OverlayController.endAppSession = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        follow(intent)
    }

    /** A mode:// link: where it goes waits in the navigator until the tabs show. */
    private fun follow(intent: Intent) {
        if (intent.getBooleanExtra(FOLLOWED, false)) return
        intent.putExtra(FOLLOWED, true)
        // Opened as "Ask Mode" (by the user's assistant, or its icon): straight into talking mode.
        if (intent.component?.className == com.piyush.mode.app.graph.ASK_MODE) {
            AppLog.log("link", "ask mode")
            shell.navigator.talk(com.piyush.mode.app.talk.TalkRequest())
            return
        }
        // "Hey Google, open Mode": Gemini only knows apps by their main name, so with Start by voice on, Mode opened
        // by the phone's assistant starts listening too. A tap on Mode's icon (the launcher opens it) doesn't.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            val from = referrer?.host
            AppLog.log("link", "opened from ${from ?: "unknown"}")
            if (from in ASSISTANTS && com.piyush.mode.app.graph.startByVoiceOn(application)) {
                AppLog.log("link", "opened by the assistant: listening")
                shell.navigator.talk(com.piyush.mode.app.talk.TalkRequest())
            }
            return
        }
        val data = intent.dataString ?: return
        // OpenRouter's sign-in page answers here; the providers sheet finishes it.
        if (data.startsWith(com.piyush.mode.app.screens.onboarding.OnboardingPresenter.SIGN_IN_CALLBACK)) {
            com.piyush.mode.app.settings.OpenRouterCallback.url.value = data
            return
        }
        val link = DeepLink.parse(data) ?: return
        AppLog.log("link", link.traceName)
        shell.navigator.pending = when (link) {
            is DeepLink.Conversation -> Route.Conversation(link.id)
            // As What went wrong's Try again: the same task again, in the same chat.
            is DeepLink.Retry -> {
                val presenter = ModeApplication.graph(this).screens.taskDebug(link.taskId)
                presenter.retry()
                presenter.close()
                link.conversationId?.let { Route.Conversation(it) }
            }
            is DeepLink.Run -> ModeApplication.graph(this).inbox.submit(link.text, com.piyush.mode.feature.tasks.domain.TaskSource.App)
                ?.conversationId?.let { Route.Conversation(it) }
            else -> null
        }
        if (link is DeepLink.Open) shell.navigator.select(AppTab.Home)
        when (link) {
            DeepLink.Listen -> shell.navigator.talk(com.piyush.mode.app.talk.TalkRequest())
            is DeepLink.Talk -> shell.navigator.talk(com.piyush.mode.app.talk.TalkRequest(
                link.conversationId,
                if (link.conversationId == null) com.piyush.mode.app.talk.TalkRequest.ENTRY_ASK else com.piyush.mode.app.talk.TalkRequest.ENTRY_CHAT,
            ))
            else -> Unit
        }
    }

    private companion object {
        /** The small window's shape (board R02: 164 × 332). */
        const val PIP_WIDTH = 1
        const val PIP_HEIGHT = 2

        /** Apps that open Mode when the user asks their assistant to (Google, Gemini, Bixby). */
        val ASSISTANTS = setOf(
            "com.google.android.googlequicksearchbox", "com.google.android.apps.bard", "com.google.android.apps.googleassistant",
            "com.samsung.android.bixby.agent", "com.samsung.android.visionintelligence",
        )

        /** Set on an intent once its link was followed, so a rotation doesn't follow it again. */
        const val FOLLOWED = "mode.followed"
    }
}
