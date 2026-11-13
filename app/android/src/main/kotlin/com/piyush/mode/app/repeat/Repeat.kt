package com.piyush.mode.app.repeat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.repeat.Frequency
import com.piyush.mode.app.screens.repeat.RepeatPresenter
import com.piyush.mode.app.screens.repeat.RepeatUiState
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeDayPicker
import com.piyush.mode.design.ModePillSegments
import com.piyush.mode.design.ModeRepeatHeader
import com.piyush.mode.design.ModeRepeatNote
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextField
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeTimeWheel

sealed interface RepeatEvent {
    data class Text(val text: String) : RepeatEvent
    data class Time(val hour: Int, val minute: Int) : RepeatEvent
    data class Often(val frequency: Frequency) : RepeatEvent
    data class ToggleDay(val day: Int) : RepeatEvent
    data object Save : RepeatEvent
    data object Cancel : RepeatEvent
}

/** Repeat: a task, a time, and which days. */
@Composable
fun RepeatScreen(state: RepeatUiState, note: String, dayLabels: List<String>, onEvent: (RepeatEvent) -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.s, bottom = ModeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.m + ModeSpacing.xxs),
    ) {
        ModeRepeatHeader(
            stringResource(R.string.repeat_title), stringResource(R.string.common_cancel), stringResource(R.string.common_save), state.canSave,
            { onEvent(RepeatEvent.Cancel) }, { onEvent(RepeatEvent.Save) },
        )
        ModeRepeatNote(stringResource(R.string.repeat_mo_note))
        ModeTextField(stringResource(R.string.repeat_task), stringResource(R.string.repeat_task_prompt), state.text, { onEvent(RepeatEvent.Text(it)) })
        ModeTimeWheel(state.hour, state.minute, { h, m -> onEvent(RepeatEvent.Time(h, m)) })
        ModePillSegments(
            listOf(
                Frequency.EveryDay to stringResource(R.string.repeat_every_day), Frequency.Weekdays to stringResource(R.string.repeat_weekdays),
                Frequency.Weekends to stringResource(R.string.repeat_weekends), Frequency.Once to stringResource(R.string.repeat_once),
            ),
            state.frequency, { onEvent(RepeatEvent.Often(it)) }, compact = true,
        )
        if (state.frequency != Frequency.Once) ModeDayPicker(dayLabels, state.isoDays, { onEvent(RepeatEvent.ToggleDay(it)) })
        ModeText(note, ModeTextStyle.Footnote, Modifier.padding(horizontal = ModeSpacing.xs), color = Mode.colors.textSecondary)
    }
}

/** Connects Repeat to its presenter; saving closes it. */
@Composable
fun RepeatRoute(graph: ModeGraph, text: String, close: () -> Unit) {
    val presenter: RepeatPresenter = rememberPresenter("repeat") { graph.screens.repeat(text) }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val note = remember(ui) { note(ui, words) }
    RepeatScreen(ui, note, words.shortDays) { event ->
        when (event) {
            is RepeatEvent.Text -> presenter.setText(event.text)
            is RepeatEvent.Time -> presenter.setTime(event.hour, event.minute)
            is RepeatEvent.Often -> presenter.setFrequency(event.frequency)
            is RepeatEvent.ToggleDay -> presenter.toggleDay(event.day)
            RepeatEvent.Save -> if (presenter.save()) close()
            RepeatEvent.Cancel -> close()
        }
    }
}

/** "Runs at 8:00 on weekdays…", or "First run tomorrow…". */
private fun note(s: RepeatUiState, w: Words): String {
    val first = s.firstRunAtMs ?: return ""
    val time = w.time(s.hour, s.minute)
    val days = when (s.frequency) {
        Frequency.EveryDay -> w.get(R.string.repeat_on_every_day)
        Frequency.Weekdays -> w.get(R.string.repeat_on_weekdays)
        Frequency.Weekends -> w.get(R.string.repeat_on_weekends)
        Frequency.Once -> w.moment(first)
        Frequency.Custom -> w.get(R.string.repeat_on_days, w.days(s.isoDays))
    }
    return if (s.frequency == Frequency.Once) w.get(R.string.repeat_note, days) else w.get(R.string.repeat_note_repeating, time, days)
}
