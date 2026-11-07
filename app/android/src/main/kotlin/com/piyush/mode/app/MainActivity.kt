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
        if (savedInstanceState == null) follow(intent)
        setContent { ModeApp(graph, shell, pages, signIn) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        follow(intent)
    }

    /** A mode:// link: where it goes waits in the navigator until the tabs show. */
    private fun follow(intent: Intent) {
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
            is DeepLink.Retry -> link.conversationId?.let { Route.Conversation(it) }
            else -> null
        }
        if (link is DeepLink.Open) shell.navigator.select(AppTab.Home)
    }
}
