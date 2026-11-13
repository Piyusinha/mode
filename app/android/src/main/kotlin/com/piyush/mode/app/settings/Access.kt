package com.piyush.mode.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.AccessPresenter
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.ModeActionSheet
import com.piyush.mode.design.ModeAddChip
import com.piyush.mode.design.ModeBadgeRow
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChipFlow
import com.piyush.mode.design.ModeChoiceRow
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModeRemovableChip
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSheet
import com.piyush.mode.design.ModeSheetAction
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextField
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeToggleRow
import com.piyush.mode.design.ModeValueRow
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** How Mode acts: ask first or full access, the words that give one task full access, codes, and what it always leaves to you. */
@Composable
fun AccessRoute(graph: ModeGraph, close: () -> Unit) {
    val presenter: AccessPresenter = rememberPresenter("access") { graph.screens.access() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var pickingAmount by remember { mutableStateOf(false) }
    val off = stringResource(R.string.settings_off)
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModePageTitle(stringResource(R.string.access_title), Modifier.padding(bottom = ModeSpacing.xl), subtitle = stringResource(R.string.access_subtitle))
            ModeCard(rows = listOf(
                { ModeChoiceRow(stringResource(R.string.access_ask_first), stringResource(R.string.access_ask_first_detail), ModeRowLeading.None, !ui.fullAccess, { presenter.setFullAccess(false) }) },
                { ModeChoiceRow(stringResource(R.string.access_full), stringResource(R.string.access_full_detail), ModeRowLeading.None, ui.fullAccess, { presenter.setFullAccess(true) }) },
            ))
            if (ui.fullAccess) {
                ModeSectionHeader(stringResource(R.string.access_spending))
                ModeCard(rows = listOf({ ModeValueRow(stringResource(R.string.access_ask_above), ui.askAbove?.let(::money) ?: off, onClick = { pickingAmount = true }) }))
                ModeFootnote(stringResource(if (ui.askAbove != null) R.string.access_ask_above_on_note else R.string.access_ask_above_off_note))
            } else {
                ModeSectionHeader(stringResource(R.string.access_one_task))
                ModeCard(rows = listOf({
                    ModeChipFlow(Modifier.padding(vertical = ModeSpacing.headerGap)) {
                        ui.words.forEach { word -> ModeRemovableChip(word, stringResource(R.string.access_remove, word), { presenter.removeWord(word) }) }
                        if (ui.canAddWord) ModeAddChip(stringResource(R.string.access_add_word), { adding = true })
                    }
                }))
                ModeFootnote(stringResource(R.string.access_one_task_note, ui.words.firstOrNull() ?: stringResource(R.string.access_example_word)))
            }
            ModeSectionHeader(stringResource(R.string.access_codes))
            ModeCard(rows = listOf({ ModeToggleRow(stringResource(R.string.access_read_codes), ui.readCodes, presenter::setReadCodes) }))
            ModeFootnote(stringResource(if (ui.readCodes) R.string.access_read_codes_note else R.string.access_ask_codes_note))
            ModeSectionHeader(stringResource(R.string.access_always_asks))
            ModeCard(rows = listOf(
                { ModeBadgeRow(stringResource(R.string.access_pin), stringResource(R.string.access_pin_detail), ModeIcon.Lock) },
                { ModeBadgeRow(stringResource(R.string.access_passwords), stringResource(R.string.access_passwords_detail), ModeIcon.Lock) },
                { ModeBadgeRow(stringResource(R.string.access_face_id), stringResource(R.string.access_face_id_detail), ModeIcon.Lock) },
            ))
            ModeFootnote(stringResource(R.string.access_always_asks_note))
        }
    }
    if (pickingAmount) {
        ModeActionSheet(
            stringResource(R.string.access_ask_above),
            listOf(ModeSheetAction(off) { presenter.setAskAbove(null) }) + AccessPresenter.AMOUNTS.map { a -> ModeSheetAction(money(a)) { presenter.setAskAbove(a) } },
            stringResource(R.string.common_cancel),
        ) { pickingAmount = false }
    }
    if (adding) {
        var word by rememberSaveable { mutableStateOf("") }
        ModeSheet({ adding = false }) {
            Column(Modifier.padding(ModeSpacing.xl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
                ModeText(stringResource(R.string.access_add_word_title), ModeTextStyle.Title2)
                ModeText(stringResource(R.string.access_add_word_message), ModeTextStyle.Callout)
                ModeTextField(stringResource(R.string.access_add_word_prompt), stringResource(R.string.access_add_word_prompt), word, { word = it })
                ModeButton(stringResource(R.string.access_add), {
                    presenter.addWord(word)
                    adding = false
                }, Modifier.fillMaxWidth(), enabled = word.isNotBlank())
            }
        }
    }
}

/** "₹500": a spending limit in the phone's currency, whole amounts. */
private fun money(amount: Double): String = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
    currency = runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull() ?: Currency.getInstance(DEFAULT_CURRENCY)
    maximumFractionDigits = 0
}.format(amount)

private const val DEFAULT_CURRENCY = "INR"
