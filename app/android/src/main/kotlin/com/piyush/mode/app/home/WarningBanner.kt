package com.piyush.mode.app.home

import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.android.ServiceRegistry
import com.piyush.mode.app.R
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.graph.brandOf
import com.piyush.mode.app.screens.home.Warning
import com.piyush.mode.app.screens.home.WarningKind
import com.piyush.mode.app.screens.home.WarningTone
import com.piyush.mode.app.screens.home.WarningsPresenter
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.core.storage.SharedPrefsStore
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeWarningBanner
import com.piyush.mode.design.ModeWarningTone

/**
 * The warning banner over every tab (boards W01–W06): what stopped working since setup, read again whenever Mode
 * comes to the front, phone control's service comes or goes, the network changes, or background mode is switched.
 */
@Composable
fun WarningBannerHost(graph: ModeGraph, pages: SystemPages, navigate: (Route) -> Unit) {
    val context = LocalContext.current
    val presenter: WarningsPresenter = rememberPresenter("warnings") { graph.screens.warnings(brand = { brandOf(SharedPrefsStore(context))?.name }) }
    val state by presenter.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { presenter.refresh() }
    LaunchedEffect(Unit) { ServiceRegistry.service.collect { presenter.refresh() } }
    val background = com.piyush.mode.app.graph.background
    if (background != null) LaunchedEffect(Unit) { background.enabledFlow.collect { presenter.refresh() } }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = presenter.refresh()
            override fun onLost(network: Network) = presenter.refresh()
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    val warning = state.current
    AnimatedVisibility(visible = warning != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        // The last warning stays for the way out.
        val shown = warning ?: return@AnimatedVisibility
        Box(Modifier.statusBarsPadding().padding(horizontal = ModeSpacing.l).padding(top = ModeSpacing.s)) {
            WarningBanner(shown, onAction = { act(shown, pages, navigate) }, onClose = presenter::dismiss)
        }
    }
}

@Composable
private fun WarningBanner(w: Warning, onAction: () -> Unit, onClose: () -> Unit) {
    val words = WORDS.getValue(w.kind)
    val brand = w.brand
    val detail = if (w.kind == WarningKind.PhoneControlStopped && brand != null) stringResource(R.string.warn_stopped_detail_brand, brand) else stringResource(words.detail)
    ModeWarningBanner(
        tone = when (w.tone) {
            WarningTone.Problem -> ModeWarningTone.Problem
            WarningTone.Warning -> ModeWarningTone.Warning
            WarningTone.Info -> ModeWarningTone.Info
        },
        icon = words.icon, title = stringResource(words.title), detail = detail, action = stringResource(words.action), onAction = onAction,
        closeLabel = if (w.closable) stringResource(R.string.warn_close) else null, onClose = if (w.closable) onClose else null,
    )
}

/** Where each banner's button goes. */
private fun act(w: Warning, pages: SystemPages, navigate: (Route) -> Unit) {
    when (w.kind) {
        WarningKind.Locked, WarningKind.PhoneControlOff -> navigate(Route.PhoneSetup)
        WarningKind.PhoneControlStopped -> pages.accessibility()
        WarningKind.BackgroundLinkLost -> pages.wirelessDebugging()
        WarningKind.BackgroundWaitingWifi -> navigate(Route.Background)
        WarningKind.NoSpeech -> pages.store(GOOGLE_APP)
    }
}

private class WarningWords(val icon: ModeIcon, val title: Int, val detail: Int, val action: Int)

private val WORDS = mapOf(
    WarningKind.Locked to WarningWords(ModeIcon.Lock, R.string.warn_locked_title, R.string.warn_locked_detail, R.string.warn_locked_action),
    WarningKind.PhoneControlOff to WarningWords(ModeIcon.Warning, R.string.warn_off_title, R.string.warn_off_detail, R.string.warn_off_action),
    WarningKind.PhoneControlStopped to WarningWords(ModeIcon.Moon, R.string.warn_stopped_title, R.string.warn_stopped_detail, R.string.warn_stopped_action),
    WarningKind.BackgroundLinkLost to WarningWords(ModeIcon.LinkOff, R.string.warn_link_title, R.string.warn_link_detail, R.string.warn_link_action),
    WarningKind.BackgroundWaitingWifi to WarningWords(ModeIcon.Wifi, R.string.warn_wifi_title, R.string.warn_wifi_detail, R.string.warn_wifi_action),
    WarningKind.NoSpeech to WarningWords(ModeIcon.MicOff, R.string.warn_speech_title, R.string.warn_speech_detail, R.string.warn_speech_action),
)

private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
