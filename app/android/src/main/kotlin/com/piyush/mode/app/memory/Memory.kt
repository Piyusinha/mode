package com.piyush.mode.app.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.memory.MemoryEntry
import com.piyush.mode.app.screens.memory.MemoryPresenter
import com.piyush.mode.app.screens.memory.MemoryUiState
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeActionSheet
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeEmptyState
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeMemoryBadge
import com.piyush.mode.design.ModeMemoryCard
import com.piyush.mode.design.ModeMemoryHeader
import com.piyush.mode.design.ModeMemoryRow
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSheetAction
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextField
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeToggleRow
import com.piyush.mode.design.MoMood

enum class Kind { Fact, Routine, Tip }

data class MemoryCardView(
    val id: String,
    val kind: Kind,
    val title: String,
    val body: String?,
    val steps: List<String>,
    val footer: String?,
    val badge: ModeMemoryBadge?,
    val text: String,
    val learnedFrom: String,
    val worked: String?,
)

data class MemorySection(val header: String?, val cards: List<MemoryCardView>)
data class AppRowView(val name: String, val initial: String, val subtitle: String)

/** Which memory page shows. */
sealed interface MemoryPage {
    data object Hub : MemoryPage
    data object AboutYou : MemoryPage
    data class Routines(val app: String?) : MemoryPage
    data object Apps : MemoryPage
    data class App(val name: String) : MemoryPage
    data class Edit(val id: String) : MemoryPage
}

/** Memory: the hub, what Mode knows about you, routines, tips by app, one app, and editing one memory. */
@Composable
fun MemoryRoute(graph: ModeGraph, page: MemoryPage, navigate: (Route) -> Unit, close: () -> Unit) {
    val presenter: MemoryPresenter = rememberPresenter("memory") { graph.screens.memory() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val cards = remember(ui) { Cards(ui, words) }
    Column(Modifier.fillMaxSize().imePadding()) {
        when (page) {
            MemoryPage.Hub -> Hub(ui, cards, presenter, navigate, close)
            MemoryPage.AboutYou -> CardsPage(
                stringResource(R.string.memory_about_you), stringResource(R.string.memory_about_you_hint),
                listOf(MemorySection(null, cards.aboutYou)), presenter, navigate, close,
            )
            is MemoryPage.Routines -> CardsPage(
                stringResource(R.string.memory_skills), stringResource(R.string.memory_routines_hint),
                cards.routines.filter { page.app == null || it.header == page.app }, presenter, navigate, close,
            )
            MemoryPage.Apps -> AppsPage(cards.apps, navigate, close)
            is MemoryPage.App -> {
                val group = ui.app(page.name)
                CardsPage(
                    page.name, group?.let { words.get(R.string.memory_app_tips_subtitle, cards.tipsCount(it.tips.size), page.name) }.orEmpty(),
                    listOf(MemorySection(null, group?.tips.orEmpty().map { cards.card(it, Kind.Tip, marked = true) })), presenter, navigate, close,
                )
            }
            is MemoryPage.Edit -> EditPage(cards.card(page.id), presenter, close)
        }
    }
}

@Composable
private fun ColumnScope.Hub(ui: MemoryUiState, cards: Cards, presenter: MemoryPresenter, navigate: (Route) -> Unit, close: () -> Unit) {
    val c = Mode.colors
    var confirming by remember { mutableStateOf(false) }
    ModePageBar(stringResource(R.string.common_back), close)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        ModeMemoryHeader(stringResource(R.string.tab_memory), stringResource(R.string.memory_subtitle))
        ModeCard(lifted = true, rows = listOf({ ModeToggleRow(stringResource(R.string.memory_learn), ui.learn, presenter::setLearn) }))
        ModeText(stringResource(R.string.memory_hub_hint), ModeTextStyle.Footnote, Modifier.padding(horizontal = ModeSpacing.xs), color = c.textSecondary)
        ModeCard(Modifier.padding(top = ModeSpacing.s), lifted = true, rows = listOf(
            { ModeMemoryRow(stringResource(R.string.memory_about_you), cards.aboutYouSummary, ModeRowLeading.IconTile(ModeIcon.Profile, c.infoSoft, c.infoText), { navigate(Route.MemoryAboutYou) }) },
            { ModeMemoryRow(stringResource(R.string.memory_skills), cards.routinesSummary, ModeRowLeading.Mo(MoMood.Idle), { navigate(Route.MemoryRoutines(null)) }) },
            { ModeMemoryRow(stringResource(R.string.memory_tips), cards.appsSummary, ModeRowLeading.IconTile(ModeIcon.Tip, c.tokens.tileOrange, c.tokens.tileOrangeText), { navigate(Route.MemoryApps) }) },
        ))
        if (!ui.isEmpty) {
            ModeButton(stringResource(R.string.memory_forget_everything), { confirming = true }, Modifier.fillMaxWidth().padding(top = ModeSpacing.l), role = ModeButtonRole.Destructive)
        }
    }
    if (confirming) {
        ModeActionSheet(
            stringResource(R.string.memory_forget_confirm),
            listOf(ModeSheetAction(stringResource(R.string.memory_forget_everything), destructive = true) { presenter.forgetEverything() }),
            stringResource(R.string.common_cancel),
        ) { confirming = false }
    }
}

@Composable
private fun ColumnScope.CardsPage(title: String, hint: String, sections: List<MemorySection>, presenter: MemoryPresenter, navigate: (Route) -> Unit, close: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val empty = sections.all { it.cards.isEmpty() }
    ModePageBar(stringResource(R.string.common_back), close) {
        if (!empty) ModeButton(stringResource(if (editing) R.string.common_done else R.string.memory_edit), {
            editing = !editing
            selected = emptySet()
        }, role = if (editing) ModeButtonRole.PillProminent else ModeButtonRole.Pill)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
        ModeMemoryHeader(title, hint)
        if (empty) ModeEmptyState(ModeIcon.Memory, stringResource(R.string.memory_nothing_here), stringResource(R.string.memory_nothing_here_message))
        sections.forEach { section ->
            section.header?.let { ModeSectionHeader(it) }
            section.cards.forEach { card ->
                ModeMemoryCard(card.title, card.body, card.steps, card.footer, card.badge, if (editing) card.id in selected else null, {
                    if (editing) selected = if (card.id in selected) selected - card.id else selected + card.id
                    else navigate(Route.MemoryEdit(card.id))
                })
            }
        }
    }
    if (editing && selected.isNotEmpty()) {
        ModeButton(stringResource(R.string.memory_delete_selected, selected.size), {
            presenter.deleteMany(selected)
            selected = emptySet()
            editing = false
        }, Modifier.fillMaxWidth().padding(ModeSpacing.screenEdge), role = ModeButtonRole.StopSoft)
    }
}

@Composable
private fun ColumnScope.AppsPage(apps: List<AppRowView>, navigate: (Route) -> Unit, close: () -> Unit) {
    ModePageBar(stringResource(R.string.common_back), close)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
        ModeMemoryHeader(stringResource(R.string.memory_tips), stringResource(R.string.memory_apps_hint))
        if (apps.isEmpty()) {
            ModeEmptyState(ModeIcon.Tip, stringResource(R.string.memory_nothing_here), stringResource(R.string.memory_nothing_here_message))
        } else {
            ModeCard(lifted = true, rows = apps.map { app -> { ModeMemoryRow(app.name, app.subtitle, ModeRowLeading.Initial(app.initial), { navigate(Route.MemoryApp(app.name)) }) } })
        }
    }
}

/** One memory in the user's words: its title and text, Save and Delete. */
@Composable
private fun ColumnScope.EditPage(card: MemoryCardView?, presenter: MemoryPresenter, close: () -> Unit) {
    if (card == null) {
        ModePageBar(stringResource(R.string.common_back), close)
        return
    }
    var title by rememberSaveable(card.id) { mutableStateOf(card.title) }
    var text by rememberSaveable(card.id) { mutableStateOf(card.text) }
    ModePageBar(stringResource(R.string.common_back), close) {
        ModeButton(stringResource(R.string.common_save), {
            if (text != card.text || title != card.title) presenter.edit(card.id, text, title)
            close()
        }, role = ModeButtonRole.PillProminent)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
        ModeMemoryHeader(
            stringResource(when (card.kind) {
                Kind.Fact -> R.string.memory_edit_fact
                Kind.Routine -> R.string.memory_edit_routine
                Kind.Tip -> R.string.memory_edit_tip
            }),
            listOfNotNull(card.learnedFrom, card.worked).joinToString(" "),
        )
        ModeTextField(stringResource(R.string.memory_title_label), stringResource(R.string.memory_title_label), title, { title = it })
        ModeTextField(stringResource(R.string.memory_learned), stringResource(R.string.memory_learned), text, { text = it })
        ModeButton(stringResource(R.string.common_delete), {
            presenter.delete(card.id)
            close()
        }, Modifier.fillMaxWidth(), role = ModeButtonRole.Destructive)
    }
}

/** Memory's state as cards and summaries, in the user's words. */
private class Cards(private val s: MemoryUiState, private val w: Words) {
    fun count(n: Int, one: Int, many: Int): String = if (n == 1) w.get(one) else w.get(many, n)
    fun tipsCount(n: Int): String = count(n, R.string.memory_tips_count_one, R.string.memory_tips_count)

    val aboutYou: List<MemoryCardView> = s.aboutYou.map { card(it, Kind.Fact) }
    val routines: List<MemorySection> = s.routines.map { g -> MemorySection(g.name, g.routines.map { card(it, Kind.Routine) }) }
    val apps: List<AppRowView> = s.apps.map { g ->
        AppRowView(g.name, g.name.take(1).uppercase(), listOfNotNull(
            tipsCount(g.tips.size), g.routines.takeIf { it.isNotEmpty() }?.let { count(it.size, R.string.memory_routines_count_one, R.string.memory_routines_count) },
        ).joinToString(" · "))
    }
    val aboutYouSummary: String = count(s.aboutYou.size, R.string.memory_things_one, R.string.memory_things)
    val routinesSummary: String = count(s.routineCount, R.string.memory_routines_learned_one, R.string.memory_routines_learned)
    val appsSummary: String = listOf(count(s.apps.size, R.string.memory_apps_count_one, R.string.memory_apps_count), tipsCount(s.tipCount)).joinToString(" · ")

    fun card(id: String): MemoryCardView? {
        s.aboutYou.firstOrNull { it.id == id }?.let { return card(it, Kind.Fact) }
        (s.routines).flatMap { it.routines }.firstOrNull { it.id == id }?.let { return card(it, Kind.Routine) }
        (s.apps + s.routines).flatMap { it.tips }.firstOrNull { it.id == id }?.let { return card(it, Kind.Tip, marked = true) }
        return null
    }

    fun card(e: MemoryEntry, kind: Kind, marked: Boolean = false): MemoryCardView {
        val day = w.day(e.learned)
        // "Learned today", but "Learned 1 Nov" and "Learned Monday".
        val inline = if (e.learned == RelativeDay.Today || e.learned == RelativeDay.Yesterday) day.lowercase() else day
        val usedTimes = e.uses.takeIf { it > 0 }?.let { count(it, R.string.memory_used_times_one, R.string.memory_used_times) }
        val (footer, badge) = when {
            e.taught && kind == Kind.Fact -> w.get(R.string.memory_you_told_mode) to null
            e.taught -> {
                val step = Regex("""\(step (\d+)\)""").find(e.evidence)?.groupValues?.get(1)
                (step?.let { w.get(R.string.memory_taught_at_step, day, it) } ?: if (IN_CHAT in e.evidence) w.get(R.string.memory_taught_in_chat, day) else day) to
                    ModeMemoryBadge.Taught(w.get(R.string.memory_you_taught_this))
            }
            kind == Kind.Tip && marked -> listOfNotNull(day, usedTimes).joinToString(" · ") to ModeMemoryBadge.Learned(w.get(R.string.memory_mode_learned))
            kind == Kind.Routine -> (if (e.uses > 0) null else w.get(R.string.memory_learned_on, inline)) to
                (if (e.uses > 0) ModeMemoryBadge.Worked(w.get(R.string.memory_worked_badge, e.successes, e.uses)) else null)
            else -> listOfNotNull(w.get(R.string.memory_learned_on, inline), usedTimes ?: if (kind == Kind.Fact) e.app?.let { w.get(R.string.memory_from_task, it) } else null)
                .joinToString(" · ") to null
        }
        val learnedFrom = when {
            e.taught -> w.get(R.string.memory_taught_note, inline)
            e.app != null -> w.get(R.string.memory_learned_from_task, e.app!!, inline)
            else -> w.get(R.string.memory_learned_note, inline)
        }
        return MemoryCardView(
            e.id, kind, e.title, e.body.ifEmpty { null }, e.steps, footer, badge, e.text, learnedFrom,
            if (e.uses > 0) w.get(R.string.memory_used_note, e.uses, e.successes) else null,
        )
    }

    private companion object {
        const val IN_CHAT = "in chat:"
    }
}
