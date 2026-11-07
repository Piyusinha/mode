package com.piyush.mode.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.ModelRole
import com.piyush.mode.app.screens.settings.PlanModelRow
import com.piyush.mode.app.screens.settings.PlanModelsPresenter
import com.piyush.mode.app.screens.settings.PlanModelsUiState
import com.piyush.mode.app.screens.settings.PlanTag
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.system.ProbeImages
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChoiceRow
import com.piyush.mode.design.ModeChoiceTag
import com.piyush.mode.design.ModeEffortRow
import com.piyush.mode.design.ModeEffortTable
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeInfoChip
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModePillSegments
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSpinner
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeWideButton

/** The models on the user's plan (ChatGPT, Claude): one for phone control, one for the brain, how hard it thinks, and Measure. */
@Composable
fun PlanModelsRoute(graph: ModeGraph, brain: Boolean, close: () -> Unit) {
    val presenter: PlanModelsPresenter = rememberPresenter("plan") { graph.screens.planModels() }
    val s by presenter.state.collectAsStateWithLifecycle()
    val w = rememberWords()
    val c = Mode.colors
    LaunchedEffect(brain) { if (brain) presenter.role(ModelRole.Brain) }
    val provider = w.get(if (s.provider == "claude_oauth") R.string.providers_claude else R.string.providers_chatgpt)
    val count = s.models.count { !it.reserve }
    val plan = listOfNotNull(provider, s.plan).joinToString(" ")
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModePageTitle(stringResource(R.string.plan_title))
            ModeInfoChip(if (count == 1) w.get(R.string.plan_on_plan_one, plan) else w.get(R.string.plan_on_plan, plan, count), Modifier.padding(start = ModeSpacing.xs, bottom = ModeSpacing.s))
            ModePillSegments(
                listOf(ModelRole.PhoneControl to stringResource(R.string.plan_phone_control), ModelRole.Brain to stringResource(R.string.plan_brain)),
                s.role, presenter::role,
            )
            ModeFootnote(stringResource(R.string.plan_role_hint), Modifier.padding(bottom = ModeSpacing.xxs))
            ModeCard(lifted = true, rows = s.models.map { m -> { ModelRow(m, s, w, presenter) } })
            val measuring = s.measuring
            if (measuring != null) {
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.wideButton), Arrangement.spacedBy(ModeSpacing.s, Alignment.CenterHorizontally), Alignment.CenterVertically) {
                    ModeSpinner()
                    ModeText(w.get(R.string.plan_measuring, measuring.first + 1, measuring.second), ModeTextStyle.Subheadline, color = c.textSecondary)
                }
            } else {
                ModeWideButton(stringResource(R.string.plan_measure), { presenter.measure(ProbeImages.make()) }, Modifier.padding(top = ModeSpacing.xxs))
            }
            s.error?.let { ModeText(it, ModeTextStyle.Footnote, Modifier.padding(horizontal = ModeSpacing.noteInset), color = c.danger) }
            ModeFootnote(stringResource(if (s.role == ModelRole.Brain) R.string.plan_brain_measure_note else R.string.plan_measure_note), style = ModeTextStyle.Caption)
        }
    }
}

@Composable
private fun ModelRow(m: PlanModelRow, s: PlanModelsUiState, w: Words, presenter: PlanModelsPresenter) {
    val c = Mode.colors
    val speed: (Long) -> String = { ms -> w.get(if (s.role == ModelRole.Brain) R.string.plan_per_answer else R.string.plan_per_step, String.format(java.util.Locale.getDefault(), SECONDS, ms / MS)) }
    if (m.reserve) {
        ModeText(stringResource(R.string.plan_reserve_note), ModeTextStyle.Footnote, Modifier.padding(vertical = ModeSpacing.m), color = c.textSecondary, maxLines = 1)
        return
    }
    val measured = when {
        m.error != null -> listOf(w.get(R.string.plan_could_not_use, m.error!!))
        m.millis != null && m.correct != null && m.total != null -> listOf(speed(m.millis!!), w.get(R.string.plan_right, m.correct!!, m.total!!))
        else -> listOf(w.get(R.string.plan_not_measured))
    }
    val line = (listOfNotNull(if (m.usesApiCredits) w.get(R.string.plan_uses_api_credits) else null, m.effort?.let { w.get(R.string.plan_effort_name, w.effort(it)) }) + measured).joinToString(" · ")
    val tag = m.tag?.let { t ->
        when (t) {
            PlanTag.BestForPhoneControl -> ModeChoiceTag(stringResource(R.string.plan_tag_best_for_phone), c.tokens.tagGreen, c.tokens.tagGreenText)
            PlanTag.BestForBrain -> ModeChoiceTag(stringResource(R.string.plan_tag_best_for_brain), c.tokens.tagGreen, c.tokens.tagGreenText)
            PlanTag.Fastest -> ModeChoiceTag(stringResource(R.string.plan_tag_fastest), c.tokens.tileOrange, c.tokens.tileOrangeText)
            PlanTag.MostAccurate -> ModeChoiceTag(stringResource(R.string.plan_tag_most_accurate), c.infoSoft, c.infoText)
            PlanTag.Recommended -> ModeChoiceTag(stringResource(R.string.settings_tag_recommended), c.cardRaised, c.textPrimary)
            PlanTag.Strongest -> ModeChoiceTag(stringResource(R.string.settings_tag_strongest), c.cardRaised, c.textPrimary)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        ModeChoiceRow(m.name, line, tile(m.id, m.name), m.selected, { presenter.choose(m.id) }, tag = tag)
        if (m.selected && m.efforts.isNotEmpty()) {
            ModeText(stringResource(R.string.plan_effort), ModeTextStyle.FootnoteEmphasis, color = c.textSecondary)
            ModePillSegments(m.efforts.map { it.effort to w.effort(it.effort) }, m.effort ?: m.efforts.first().effort, presenter::effort, compact = true)
            ModeEffortTable(
                Triple(stringResource(R.string.plan_effort), stringResource(R.string.plan_speed), stringResource(R.string.plan_accuracy)),
                m.efforts.map { e ->
                    ModeEffortRow(
                        w.effort(e.effort), e.millis?.let(speed) ?: DASH,
                        if (e.error != null) w.get(R.string.plan_failed) else if (e.correct != null && e.total != null) w.get(R.string.plan_right, e.correct!!, e.total!!) else DASH,
                        e.selected,
                    )
                },
                Modifier.padding(bottom = ModeSpacing.l),
            )
        }
    }
}

private const val SECONDS = "%.1f"
private const val MS = 1000.0
private const val DASH = "—"
