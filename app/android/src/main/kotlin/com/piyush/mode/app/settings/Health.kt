package com.piyush.mode.app.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.HealthFix
import com.piyush.mode.app.screens.settings.HealthItem
import com.piyush.mode.app.screens.settings.HealthPresenter
import com.piyush.mode.app.screens.settings.HealthState
import com.piyush.mode.app.screens.settings.HealthUiState
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeHealthBanner
import com.piyush.mode.design.ModeHealthRow
import com.piyush.mode.design.ModeHealthStatus
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSpacing

data class HealthRowView(val group: String, val title: String, val detail: String, val icon: ModeIcon, val status: ModeHealthStatus, val repair: Boolean, val opens: String?)

/** Health and repair: every part of Mode checked, what needs attention, Repair, Check again, Share report. */
@Composable
fun HealthRoute(graph: ModeGraph, pages: SystemPages, navigate: (Route) -> Unit, close: () -> Unit) {
    val presenter: HealthPresenter = rememberPresenter("health") { graph.screens.health() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val context = LocalContext.current
    val rows = remember(ui) { rows(ui, words) }
    LaunchedEffect(Unit) { presenter.check() }
    val open: (String) -> Unit = { page ->
        when (page) {
            "voice" -> navigate(Route.Voice)
            "listening" -> navigate(Route.Listening)
            "providers" -> navigate(Route.Settings)
            "phone-setup" -> navigate(Route.PhoneSetup)
            "background" -> navigate(Route.Background)
            "google-app" -> pages.store(GOOGLE_APP)
            else -> pages.appInfo()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModePageTitle(stringResource(R.string.health_title), Modifier.padding(bottom = ModeSpacing.m))
            when {
                ui.checking || ui.repairing -> ModeHealthBanner(true, stringResource(if (ui.repairing) R.string.health_repairing else R.string.health_checking), stringResource(R.string.health_checking_detail), busy = true)
                ui.problems == 0 -> ModeHealthBanner(true, stringResource(R.string.health_all_good), stringResource(R.string.health_checked_now))
                else -> ModeHealthBanner(
                    false, attention(ui, words), canRepair(ui, words),
                    action = if (ui.repairable > 0) stringResource(R.string.health_repair) else null, onAction = presenter::repair,
                )
            }
            listOf(
                R.string.health_phone_control to "phone", R.string.settings_ai to "ai", R.string.voice_section to "voice", R.string.health_this_iphone to "iphone",
            ).forEach { (title, group) ->
                val inGroup = rows.filter { it.group == group }
                if (inGroup.isNotEmpty()) {
                    ModeSectionHeader(stringResource(title))
                    ModeCard(rows = inGroup.map { r ->
                        {
                            ModeHealthRow(
                                r.title, r.detail, r.icon, r.status, action = if (r.repair || r.opens != null) stringResource(R.string.health_fix) else null,
                                opens = !r.repair, onAction = if (r.repair) presenter::repair else r.opens?.let { page -> { open(page) } },
                            )
                        }
                    })
                }
            }
        }
        Row(Modifier.padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.s), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m - ModeSpacing.xxs)) {
            ModeButton(stringResource(R.string.health_share), {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, presenter.report()), null))
            }, Modifier.weight(1f), role = ModeButtonRole.Secondary)
            ModeButton(
                stringResource(if (ui.checking) R.string.health_checking else R.string.health_check_again), presenter::check, Modifier.weight(1f),
                role = ModeButtonRole.Primary, enabled = !ui.checking && !ui.repairing,
            )
        }
    }
}

private fun attention(s: HealthUiState, w: Words) = if (s.problems == 1) w.get(R.string.health_need_attention_one) else w.get(R.string.health_need_attention, s.problems)

private fun canRepair(s: HealthUiState, w: Words): String {
    if (s.repairable == 0) return w.get(R.string.health_needs_you)
    val rest = s.problems - s.repairable
    return when {
        rest == 0 -> w.get(R.string.health_can_repair, s.repairable)
        rest == 1 -> w.get(R.string.health_can_repair_rest_one, s.repairable, rest)
        else -> w.get(R.string.health_can_repair_rest, s.repairable, rest)
    }
}

/** Google's app holds the speech recognizer most phones use. */
private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

/** The iPhone's pairing, VPN and reach rows don't exist on Android. */
private val IPHONE_ONLY = setOf(HealthItem.Paired, HealthItem.Vpn, HealthItem.Reach)

private fun rows(s: HealthUiState, w: Words): List<HealthRowView> = s.rows.filter { it.item !in IPHONE_ONLY }.map { r ->
    val (title, icon) = when (r.item) {
        HealthItem.Paired -> w.get(R.string.health_paired) to ModeIcon.ActionButton
        HealthItem.Vpn -> w.get(R.string.health_vpn) to ModeIcon.Vpn
        HealthItem.Reach -> w.get(R.string.health_reach) to ModeIcon.ArrowRight
        HealthItem.Unlocked -> w.get(R.string.health_unlocked) to ModeIcon.Lock
        HealthItem.Engine -> w.get(R.string.phone_control) to ModeIcon.Tap
        HealthItem.Battery -> w.get(R.string.phone_battery) to ModeIcon.Battery
        HealthItem.Sleeping -> w.get(R.string.phone_sleeping) to ModeIcon.Moon
        HealthItem.Autostart -> w.get(R.string.phone_autostart) to ModeIcon.Rocket
        HealthItem.Popups -> w.get(R.string.phone_popups) to ModeIcon.Popup
        HealthItem.BackgroundLink -> w.get(R.string.bg_row_title) to ModeIcon.Background
        HealthItem.Screen -> w.get(R.string.health_screen) to ModeIcon.Screens
        HealthItem.Ai -> w.get(R.string.health_ai) to ModeIcon.Sparkle
        HealthItem.Actions -> w.get(R.string.health_actions) to ModeIcon.Tap
        HealthItem.Microphone -> w.get(R.string.health_microphone) to ModeIcon.Microphone
        HealthItem.Listening -> w.get(R.string.health_listening) to ModeIcon.Microphone
        HealthItem.Speaking -> w.get(R.string.health_speaking) to ModeIcon.Speaker
        HealthItem.Notifications -> w.get(R.string.health_notifications) to ModeIcon.Bell
    }
    val status = when (r.state) {
        HealthState.Ok -> ModeHealthStatus.Ok
        HealthState.Warn -> ModeHealthStatus.Warning
        HealthState.Repaired -> ModeHealthStatus.Repaired
        HealthState.Busy -> ModeHealthStatus.Busy
        HealthState.Waiting -> ModeHealthStatus.Info
        HealthState.Checking -> ModeHealthStatus.Checking
    }
    HealthRowView(r.item.group, title, if (r.state == HealthState.Repaired) w.get(R.string.health_repaired) else r.detail, icon, status, r.fix == HealthFix.Repair, if (r.fix == HealthFix.Open) r.opens else null)
}
