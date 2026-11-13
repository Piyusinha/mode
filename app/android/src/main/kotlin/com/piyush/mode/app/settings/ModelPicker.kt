package com.piyush.mode.app.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.ModelChoice
import com.piyush.mode.app.screens.settings.SettingsPresenter
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChoiceRow
import com.piyush.mode.design.ModeChoiceTag
import com.piyush.mode.design.ModeFilterChip
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeLogo
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSearchField
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.feature.settings.domain.ModelTag

/** A model as the picker lists it. */
data class ModelItem(val id: String, val name: String, val tag: ModelTag?, val subtitle: String, val cost: Double, val isMain: Boolean, val isStuck: Boolean)

/** Which models show: Mode's picks, all of them by search, cheapest first, or one maker's. */
sealed interface ModelFilter {
    data object All : ModelFilter
    data object Recommended : ModelFilter
    data object Cheapest : ModelFilter
    data class Maker(val id: String) : ModelFilter
}

/** One model for the main job, or for when Mode is stuck: search, chips, and the models with their tags. */
@Composable
fun ModelPickerRoute(graph: ModeGraph, stuck: Boolean, close: () -> Unit) {
    val presenter: SettingsPresenter = rememberPresenter("picker") { graph.screens.settings() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    var query by rememberSaveable { mutableStateOf(ui.query) }
    var filter by remember { mutableStateOf<ModelFilter>(ModelFilter.All) }
    val picks = remember(ui.picks) { ui.picks.map { item(it, !ui.showsCost, words) } }
    val results = remember(ui.results) { ui.results.map { item(it, !ui.showsCost, words) } }
    val listed = when (filter) {
        ModelFilter.Recommended -> picks
        ModelFilter.Cheapest -> results.sortedBy { it.cost }
        is ModelFilter.Maker -> results
        ModelFilter.All -> if (query.isEmpty()) picks else results
    }
    val title = stringResource(if (stuck) R.string.settings_when_stuck_short else R.string.settings_main_model)
    val c = Mode.colors
    fun pickFilter(f: ModelFilter) {
        filter = f
        presenter.filterMaker(
            when (f) {
                ModelFilter.All, ModelFilter.Recommended -> null
                ModelFilter.Cheapest -> ""
                is ModelFilter.Maker -> f.id
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModePageTitle(title, Modifier.padding(bottom = ModeSpacing.s))
            ModeSearchField(stringResource(R.string.settings_search_count, ui.modelCount), query, {
                query = it
                presenter.search(it)
            })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                ModeFilterChip(stringResource(R.string.settings_all), filter == ModelFilter.All, { pickFilter(ModelFilter.All) })
                ModeFilterChip(stringResource(R.string.settings_tag_recommended), filter == ModelFilter.Recommended, { pickFilter(ModelFilter.Recommended) })
                if (ui.showsCost) ModeFilterChip(stringResource(R.string.settings_tag_cheapest), filter == ModelFilter.Cheapest, { pickFilter(ModelFilter.Cheapest) })
                ui.makers.forEach { m -> ModeFilterChip(makerName(m), filter == ModelFilter.Maker(m), { pickFilter(ModelFilter.Maker(m)) }) }
            }
            val rows = buildList<@Composable () -> Unit> {
                if (stuck) add {
                    ModeChoiceRow(stringResource(R.string.settings_never), stringResource(R.string.settings_never_detail), ModeRowLeading.None, ui.stuckModel == null, { presenter.chooseStuckModel(null) })
                }
                listed.forEach { m ->
                    add {
                        ModeChoiceRow(
                            m.name, m.subtitle, tile(m.id, m.name), if (stuck) m.isStuck else m.isMain,
                            { if (stuck) presenter.chooseStuckModel(m.id) else presenter.chooseModel(m.id) },
                            tag = m.tag?.let { t ->
                                when (t) {
                                    ModelTag.Recommended -> ModeChoiceTag(stringResource(R.string.settings_tag_recommended), c.cardRaised, c.textPrimary)
                                    ModelTag.Cheapest -> ModeChoiceTag(stringResource(R.string.settings_tag_cheapest), c.tokens.tagGreen, c.tokens.tagGreenText)
                                    ModelTag.Strongest -> ModeChoiceTag(stringResource(R.string.settings_tag_strongest), c.cardRaised, c.textPrimary)
                                }
                            },
                        )
                    }
                }
            }
            if (rows.isNotEmpty()) ModeCard(lifted = true, rows = rows)
            if (listed.isEmpty()) ModeFootnote(stringResource(R.string.settings_no_models))
            ModeFootnote(stringResource(if (ui.showsCost) R.string.settings_picker_note else R.string.settings_picker_plan_note), Modifier.padding(top = ModeSpacing.xxs), style = ModeTextStyle.Caption)
        }
    }
}

private fun item(c: ModelChoice, onPlan: Boolean, w: Words): ModelItem {
    val cost = if (onPlan) w.get(R.string.settings_on_your_plan) else w.get(R.string.settings_per_task, w.money(c.costPerTaskUsd))
    return ModelItem(c.id, c.name, c.tag, listOfNotNull(c.note.ifEmpty { null }, cost).joinToString(" · "), c.costPerTaskUsd, c.isMain, c.isStuck)
}

/** A model's tile: its maker's logo, or the maker's first letter. */
@Composable
fun tile(id: String, name: String): ModeRowLeading {
    val c = Mode.colors
    val logo = ModeLogo.forModel(id)
    return when (logo) {
        ModeLogo.Claude -> ModeRowLeading.LogoTile(logo, c.tokens.tileOrange, c.tokens.tileOrangeText)
        ModeLogo.OpenAI -> ModeRowLeading.LogoTile(logo, c.tokens.tileGray, c.textPrimary)
        null -> ModeRowLeading.LetterTile((if ("/" in id) id.substringBefore('/') else name).take(1).uppercase(), c.tintSoft, c.tint)
        else -> ModeRowLeading.LogoTile(logo, c.tintSoft, c.tint)
    }
}

/** "OpenAI", "xAI", "Meta": a maker's id as people say it. */
fun makerName(id: String): String = when (id.lowercase()) {
    "openai" -> "OpenAI"
    "x-ai" -> "xAI"
    "meta-llama" -> "Meta"
    "mistralai" -> "Mistral"
    "deepseek" -> "DeepSeek"
    "z-ai" -> "Z.ai"
    else -> id.replaceFirstChar { it.uppercase() }
}
