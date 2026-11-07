package com.piyush.mode.app.settings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.ProviderDetailPresenter
import com.piyush.mode.app.screens.settings.ProviderDetailUiState
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeLeading
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeStatusLine
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeUsageBar
import com.piyush.mode.design.ModeValueRow
import com.piyush.mode.design.ModeWeekBars
import com.piyush.mode.design.ModeWideButton
import com.piyush.mode.design.ModeWideStyle
import com.piyush.mode.design.MoMood
import com.piyush.mode.design.ModeMo
import com.piyush.mode.design.ModeSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One AI provider: who's signed in, what Mode used (or credits left), its models, and sign out. */
@Composable
fun ProviderDetailRoute(graph: ModeGraph, id: String, pages: SystemPages, navigate: (Route) -> Unit, close: () -> Unit) {
    val presenter: ProviderDetailPresenter = rememberPresenter("provider:$id") { graph.screens.providerDetail(id) }
    val s by presenter.state.collectAsStateWithLifecycle()
    val w = rememberWords()
    val c = Mode.colors
    val kind = ProviderWords.kind(s.id)
    val title = ProviderWords.title(kind, w)
    LaunchedEffect(Unit) { presenter.refresh() }
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.headerGap), verticalAlignment = Alignment.CenterVertically) {
                ModeLeading(ProviderWords.tile(kind))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
                    ModeText(title, ModeTextStyle.Title, maxLines = 1)
                    ModeStatusLine(
                        stringResource(if (!s.connected) R.string.providers_not_connected else if (s.inUse) R.string.account_in_use else R.string.account_not_in_use),
                        if (s.connected) c.success else c.ring,
                    )
                }
                ModeMo(if (s.inUse) MoMood.Idle else MoMood.Sleep, ModeSize.moProvider)
            }
            ModeSectionHeader(stringResource(R.string.account_account))
            val notShared = stringResource(R.string.account_not_shared)
            val account: List<Pair<String, String>> = if (kind == ProviderWords.Kind.OpenRouter) listOf(
                stringResource(R.string.account_key) to (s.keyEnding?.let { "$KEY_PREFIX$it" } ?: notShared),
                stringResource(R.string.account_key_name) to (s.keyLabel ?: notShared),
            ) else listOfNotNull(
                stringResource(R.string.account_signed_in_as) to (s.email ?: notShared),
                s.name?.let { stringResource(R.string.account_name) to it },
                stringResource(R.string.account_plan) to (s.plan ?: notShared),
            )
            ModeCard(lifted = true, rows = account.map { (t, v) -> { ModeValueRow(t, v) } })
            ModeSectionHeader(stringResource(if (kind == ProviderWords.Kind.OpenRouter) R.string.account_credits else R.string.account_usage))
            Usage(kind, s, w)
            ModeWideButton(
                stringResource(when (kind) {
                    ProviderWords.Kind.ChatGpt -> R.string.account_see_chat_gpt_usage
                    ProviderWords.Kind.Claude -> R.string.account_see_claude_usage
                    ProviderWords.Kind.OpenRouter -> R.string.account_add_credits
                }),
                { pages.web(when (kind) {
                    ProviderWords.Kind.ChatGpt -> CHATGPT_USAGE
                    ProviderWords.Kind.Claude -> CLAUDE_USAGE
                    ProviderWords.Kind.OpenRouter -> OPENROUTER_CREDITS
                }) },
                trailingIcon = ModeIcon.External,
            )
            if (s.connected && !s.inUse) ModeWideButton(stringResource(R.string.account_use, title), presenter::use, style = ModeWideStyle.Ink)
            if (kind != ProviderWords.Kind.OpenRouter) {
                ModeSectionHeader(stringResource(R.string.account_models))
                ModeCard(lifted = true, rows = listOf(
                    { ModeValueRow(stringResource(R.string.plan_phone_control), model(s.phoneModel, s.phoneEffort, w), onClick = { navigate(Route.PlanModels(false)) }) },
                    { ModeValueRow(stringResource(R.string.plan_brain), model(s.brainModel, s.brainEffort, w), onClick = { navigate(Route.PlanModels(true)) }) },
                ))
            }
            if (s.connected) {
                ModeWideButton(
                    stringResource(when (kind) {
                        ProviderWords.Kind.ChatGpt -> R.string.account_sign_out_chat_gpt
                        ProviderWords.Kind.Claude -> R.string.account_sign_out_claude
                        ProviderWords.Kind.OpenRouter -> R.string.account_remove_key
                    }),
                    presenter::disconnect, Modifier.padding(top = ModeSpacing.xs), style = ModeWideStyle.Danger,
                )
            }
        }
    }
}

@Composable
private fun Usage(kind: ProviderWords.Kind, s: ProviderDetailUiState, w: Words) {
    val c = Mode.colors
    val todayLine = w.get(R.string.account_steps_and_tasks, s.todaySteps, s.todayTasks)
    when (kind) {
        ProviderWords.Kind.ChatGpt -> {
            ModeCard(lifted = true, rows = listOf(
                {
                    Column(Modifier.padding(top = ModeSpacing.headerGap)) {
                        Column(Modifier.padding(bottom = ModeSpacing.m), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m - ModeSpacing.xxs)) {
                            Row {
                                ModeText(stringResource(R.string.account_this_week), ModeTextStyle.SubheadlineEmphasis, Modifier.weight(1f))
                                ModeText(steps(s.weekSteps, w), ModeTextStyle.Footnote, color = c.textSecondary)
                            }
                            ModeWeekBars(s.daySteps, w.shortDays, s.todayIndex)
                        }
                        ModeValueRow(stringResource(R.string.account_today), todayLine)
                    }
                },
                { ModeValueRow(stringResource(R.string.account_tasks_this_week), s.weekTasks.toString()) },
                {
                    ModeValueRow(
                        stringResource(R.string.account_plan_limit), s.limitReachedAt?.let { w.get(R.string.account_reached_at, w.timeOfDay(it)) } ?: stringResource(R.string.account_available),
                        dot = if (s.limitReachedAt != null) c.warning else c.success,
                    )
                },
            ))
            ModeFootnote(stringResource(R.string.account_chat_gpt_note), style = ModeTextStyle.Caption)
        }
        ProviderWords.Kind.Claude -> {
            ModeCard(lifted = true, rows = s.limits.map<_, @Composable () -> Unit> { l ->
                {
                    ModeUsageBar(
                        stringResource(if (l.session) R.string.account_session else R.string.account_week), w.get(R.string.account_used, (l.usedFraction * 100).toInt()),
                        l.usedFraction, if (l.usedFraction < WARN_AT) c.success else c.warning, l.resetsAt?.let { w.get(R.string.account_resets, when_(it, w)) },
                    )
                }
            } + listOf<@Composable () -> Unit>({ ModeValueRow(stringResource(R.string.account_mode_today), todayLine) }))
            ModeFootnote(s.usageUpdatedAt?.let { w.get(R.string.account_claude_note_updated, when_(it, w)) } ?: stringResource(R.string.account_claude_note), style = ModeTextStyle.Caption)
        }
        ProviderWords.Kind.OpenRouter -> ModeCard(lifted = true, rows = buildList<@Composable () -> Unit> {
            add {
                Column {
                    s.creditsLeft?.let { left ->
                        val total = s.creditsTotal
                        ModeUsageBar(
                            stringResource(R.string.account_credits_left), w.money(left), if (total != null && total > 0) left / total else 0.0,
                            c.brand, total?.let { w.get(R.string.account_of_total, w.money(it)) }, prominent = true,
                        )
                    }
                    ModeValueRow(stringResource(R.string.account_spent_today), "${w.money(s.todayCostUsd)} · ${s.todayTasks}")
                }
            }
            add { ModeValueRow(stringResource(R.string.account_week), w.money(s.weekCostUsd)) }
            s.maxCostPerTaskUsd?.let { perTask -> add { ModeValueRow(stringResource(R.string.account_limit_per_task), w.money(perTask)) } }
        })
    }
}

private fun steps(n: Int, w: Words) = if (n == 1) w.get(R.string.account_steps_one) else w.get(R.string.account_steps, n)

private fun model(name: String?, effort: String?, w: Words) = listOfNotNull(name, effort?.let(w::effort)).joinToString(" · ")

/** "Monday 18:00": when a limit resets or the usage was read. */
private fun when_(ms: Long, w: Words): String =
    DateTimeFormatter.ofPattern("EEEE HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private const val KEY_PREFIX = "sk-or-…"
private const val WARN_AT = 0.7
private const val CHATGPT_USAGE = "https://chatgpt.com/settings/usage"
private const val CLAUDE_USAGE = "https://claude.ai/settings/usage"
private const val OPENROUTER_CREDITS = "https://openrouter.ai/settings/credits"
