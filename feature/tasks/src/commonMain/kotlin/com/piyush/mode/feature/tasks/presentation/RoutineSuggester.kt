package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.Routines
import com.piyush.mode.feature.tasks.domain.TaskStatus
import kotlinx.datetime.TimeZone

/**
 * Suggests a routine when it is about due ("Your usual around now: book a cab to work"), as a notification
 * that opens Mode; never runs it. Off unless Settings → suggest routines is on; a routine dismissed twice
 * (suggested and not run) is not suggested again.
 */
public class RoutineSuggester(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val prefs: KeyValueStore,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /** On foreground and at launch: at most one suggestion per routine per day. Returns the suggested text, if any. */
    public fun check(): String? {
        if (!settings.settings.value.suggestRoutines) return null
        val recent = tasks.latest(LOOKBACK).map { Routines.Candidate(it.text, it.createdAt, it.status == TaskStatus.Done) }
        val routines = Routines.detect(recent, zone(), now())
        val due = Routines.due(routines, recent, now(), zone()) ?: return null
        val dismissals = prefs.getString(DISMISSED + due.key)?.toIntOrNull() ?: 0
        if (dismissals >= MAX_DISMISSALS) return null
        val today = now() / DAY_MS
        if (prefs.getString(SUGGESTED + due.key)?.toLongOrNull() == today) return null
        prefs.putString(SUGGESTED + due.key, today.toString())
        prefs.putString(DISMISSED + due.key, (dismissals + 1).toString())
        notifier.post("Mode", "Your usual around now: ${due.text.take(80)}. Open Mode to run it.")
        AppLog.log("routine", "suggested \"${due.text.take(40)}\" (${due.times} times before)")
        return due.text
    }

    /** The user ran a routine's task: it was not a dismissal. */
    public fun ran(text: String) {
        prefs.putString(DISMISSED + Routines.key(text), "0")
    }

    private companion object {
        const val LOOKBACK = 200L
        const val MAX_DISMISSALS = 2
        const val DAY_MS = 86_400_000L
        const val SUGGESTED = "routine.suggested."
        const val DISMISSED = "routine.dismissed."
    }
}
