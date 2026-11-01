package com.piyush.mode.app.screens.repeat

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.feature.schedules.domain.ScheduleRule
import com.piyush.mode.feature.schedules.presentation.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

public enum class Frequency { EveryDay, Weekdays, Weekends, Once, Custom }

public data class RepeatUiState(
    val text: String = "",
    val hour: Int = 8,
    val minute: Int = 0,
    val frequency: Frequency = Frequency.EveryDay,
    /** 1 = Monday … 7 = Sunday. */
    val isoDays: Set<Int> = (1..7).toSet(),
    /** When it would first run, for the note under the form. */
    val firstRunAtMs: Long? = null,
    val canSave: Boolean = false,
)

/** The Repeat sheet: a task, a time, and which days. */
public class RepeatPresenter(
    parent: CoroutineScope,
    initialText: String,
    private val scheduler: Scheduler,
    private val clock: ScreenClock = ScreenClock(),
) : Presenter<RepeatUiState>(parent, RepeatUiState()) {

    init {
        update { it.copy(text = initialText) }
    }

    public fun setText(text: String): Unit = update { it.copy(text = text) }

    public fun setTime(hour: Int, minute: Int): Unit = update { it.copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59)) }

    public fun setFrequency(frequency: Frequency): Unit = update {
        val days = when (frequency) {
            Frequency.EveryDay -> ScheduleRule.EVERY_DAY
            Frequency.Weekdays -> ScheduleRule.WEEKDAYS
            Frequency.Weekends -> ScheduleRule.WEEKEND
            Frequency.Once -> emptySet()
            Frequency.Custom -> it.isoDays.toDays()
        }
        it.copy(frequency = frequency, isoDays = days.map(DayOfWeek::isoDayNumber).toSet())
    }

    public fun toggleDay(isoDay: Int): Unit = update {
        val days = if (isoDay in it.isoDays) it.isoDays - isoDay else it.isoDays + isoDay
        it.copy(isoDays = days, frequency = frequencyOf(days))
    }

    /** Saves the schedule; false when the form isn't complete. */
    public fun save(): Boolean {
        val s = state.value
        if (!s.canSave) return false
        return scheduler.add(s.text.trim(), rule(s)) != null
    }

    private fun update(change: (RepeatUiState) -> RepeatUiState) {
        val next = change(mutableState.value)
        mutableState.value = next.copy(
            canSave = next.text.isNotBlank() && (next.frequency == Frequency.Once || next.isoDays.isNotEmpty()),
            firstRunAtMs = rule(next).nextRun(clock.now(), clock.zone()),
        )
    }

    private fun rule(s: RepeatUiState) = ScheduleRule(s.hour, s.minute, if (s.frequency == Frequency.Once) emptySet() else s.isoDays.toDays())

    private fun Set<Int>.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { it.isoDayNumber in this }.toSet()

    private fun frequencyOf(days: Set<Int>): Frequency = when (days.toDays()) {
        ScheduleRule.EVERY_DAY -> Frequency.EveryDay
        ScheduleRule.WEEKDAYS -> Frequency.Weekdays
        ScheduleRule.WEEKEND -> Frequency.Weekends
        emptySet<DayOfWeek>() -> Frequency.Once
        else -> Frequency.Custom
    }
}
