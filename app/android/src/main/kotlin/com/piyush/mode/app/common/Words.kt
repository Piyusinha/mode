package com.piyush.mode.app.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.piyush.mode.app.R
import com.piyush.mode.app.screens.common.PartOfDay
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.design.ModeIcon
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Turns the presenters' values into words, in the user's language (Format on the iPhone).
 * Routes call these while mapping state; views only show the result.
 */
class Words(private val context: Context) {
    private val locale: Locale get() = context.resources.configuration.locales[0]

    fun get(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun day(day: RelativeDay): String = when (day) {
        RelativeDay.Today -> get(R.string.day_today)
        RelativeDay.Yesterday -> get(R.string.day_yesterday)
        is RelativeDay.Weekday -> DayOfWeek.of(day.isoDay).getDisplayName(TextStyle.FULL, locale)
        is RelativeDay.Date -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
            .format(Instant.ofEpochMilli(day.epochMs).atZone(ZoneId.systemDefault()))
    }

    /** "8:13", in the phone's clock style. */
    fun timeOfDay(epochMs: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMs))

    fun time(hour: Int, minute: Int): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(java.time.LocalTime.of(hour, minute))

    /** "62 s", "0.9 s": durations as the boards show them. */
    fun seconds(value: Double): String {
        val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (value < 10) 1 else 0 }
        return get(R.string.format_seconds, number.format(value))
    }

    /** "42 s", "3 min", "3 min 12 s". */
    fun duration(seconds: Long): String = when {
        seconds < 60 -> get(R.string.tasks_duration_seconds, seconds)
        seconds % 60 == 0L -> get(R.string.tasks_duration_minutes, seconds / 60)
        else -> get(R.string.tasks_duration_minutes_seconds, seconds / 60, seconds % 60)
    }

    /** "Today 19:00", "Tomorrow 9:00", "Thu, 7 Nov · 10:00". */
    fun upcoming(epochMs: Long): String {
        val zone = ZoneId.systemDefault()
        val at = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = java.time.LocalDate.now(zone)
        val time = timeOfDay(epochMs)
        return when (at.toLocalDate()) {
            today -> "${get(R.string.day_today)} $time"
            today.plusDays(1) -> "${get(R.string.day_tomorrow)} $time"
            else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(at) + " · " + time
        }
    }

    /** One-letter day names, Monday first. */
    val shortDays: List<String>
        get() = (1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW_STANDALONE, locale) }

    /** "in 3 hours", "tomorrow": when something next happens, relative to now. */
    fun moment(epochMs: Long): String =
        android.text.format.DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()

    /** "Every day", "Weekdays", "Weekends", or the days by name ("Mon, Wed and Fri"). */
    fun days(isoDays: Set<Int>): String = when (isoDays) {
        (1..7).toSet() -> get(R.string.repeat_every_day)
        (1..5).toSet() -> get(R.string.repeat_weekdays)
        setOf(6, 7) -> get(R.string.repeat_weekends)
        else -> {
            val names = isoDays.sorted().map { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
            android.icu.text.ListFormatter.getInstance(locale).format(names)
        }
    }

    fun greeting(part: PartOfDay, name: String?): String = when (part) {
        PartOfDay.Morning -> if (name != null) get(R.string.home_greeting_morning_named, name) else get(R.string.home_greeting_morning)
        PartOfDay.Afternoon -> if (name != null) get(R.string.home_greeting_afternoon_named, name) else get(R.string.home_greeting_afternoon)
        PartOfDay.Evening -> if (name != null) get(R.string.home_greeting_evening_named, name) else get(R.string.home_greeting_evening)
        PartOfDay.Night -> if (name != null) get(R.string.home_greeting_night_named, name) else get(R.string.home_greeting_night)
    }

    /** A voice or listening service's name, by its stored id ("apple" is the phone's own). */
    fun voiceProvider(id: String): String = when (id) {
        "apple" -> get(R.string.voice_apple)
        "fish" -> get(R.string.voice_fish)
        "elevenlabs" -> get(R.string.voice_eleven)
        "soniox" -> get(R.string.voice_soniox)
        "deepgram" -> get(R.string.voice_deepgram)
        else -> id
    }

    /** How a task ended: its word and icon. */
    fun badge(badge: TaskBadge): Pair<String, ModeIcon> = when (badge) {
        TaskBadge.Done -> get(R.string.badge_done) to ModeIcon.Done
        TaskBadge.GaveUp -> get(R.string.badge_gave_up) to ModeIcon.Warning
        TaskBadge.Stopped -> get(R.string.badge_stopped) to ModeIcon.Stopped
        TaskBadge.Failed -> get(R.string.badge_failed) to ModeIcon.Failed
        TaskBadge.NotStarted -> get(R.string.badge_not_started) to ModeIcon.Info
    }
}

/** The words for the current screen. */
@Composable
fun rememberWords(): Words {
    val context = LocalContext.current
    return remember(context) { Words(context) }
}
