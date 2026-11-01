package com.piyush.mode.feature.schedules.presentation

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.feature.schedules.data.ScheduleRepository
import com.piyush.mode.feature.schedules.domain.Schedule
import com.piyush.mode.feature.schedules.domain.ScheduleRule
import com.piyush.mode.feature.tasks.domain.TaskInbox
import com.piyush.mode.feature.tasks.domain.TaskSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Hands repeating tasks to the inbox when they are due: at launch, whenever
 * Mode comes to the front, and every [tickMs] while it is open.
 *
 * iOS doesn't let Mode start work by itself while it isn't running, so each
 * schedule also sets a notification for its next time; tapping it opens Mode,
 * which then runs the task. A run missed by more than [MAX_LATE_MS] (the phone
 * was off, the notification ignored) is skipped rather than run late.
 */
@OptIn(ExperimentalTime::class)
public class Scheduler(
    private val schedules: ScheduleRepository,
    private val inbox: TaskInbox,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val tickMs: Long = 30_000,
) {
    public val all: Flow<List<Schedule>> get() = schedules.observe()

    private var ticker: Job? = null

    /** Starts checking: now, on every return to the front, and on a timer while open. */
    public fun start(lifecycle: AppLifecycle) {
        checkDue()
        lifecycle.onForeground { checkDue() }
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(tickMs)
                checkDue()
            }
        }
    }

    public fun add(text: String, rule: ScheduleRule): Schedule? {
        if (text.isBlank()) return null
        notifier.requestPermission()
        val time = now()
        val schedule = schedules.add(text, rule, rule.nextRun(time, zone()), time)
        remind(schedule)
        AppLog.log("schedule", "added \"${schedule.text}\" ${rule.label()}")
        return schedule
    }

    public fun setEnabled(id: String, enabled: Boolean) {
        val schedule = schedules.get(id) ?: return
        val next = if (enabled) schedule.rule.nextRun(now(), zone()) else null
        schedules.setEnabled(id, enabled, next)
        if (enabled) schedules.get(id)?.let(::remind) else notifier.cancel(notificationId(id))
    }

    public fun delete(id: String) {
        schedules.delete(id)
        notifier.cancel(notificationId(id))
    }

    /** Submits every schedule that is due, and works out when each is due next. */
    public fun checkDue() {
        val time = now()
        for (s in schedules.due(time)) {
            val dueAt = s.nextRunAt ?: continue
            if (time - dueAt > MAX_LATE_MS) {
                AppLog.log("schedule", "skipped \"${s.text}\": it was due ${(time - dueAt) / 60_000} minutes ago")
            } else {
                inbox.submit(s.text, TaskSource.Schedule)
            }
            val next = if (s.rule.once) null else s.rule.nextRun(time, zone())
            schedules.ran(s.id, time, next)
            schedules.get(s.id)?.takeIf { it.enabled }?.let(::remind) ?: notifier.cancel(notificationId(s.id))
        }
    }

    private fun remind(s: Schedule) {
        val at = s.nextRunAt ?: return
        notifier.schedule(notificationId(s.id), "Mode · ${s.rule.label()}", "${s.text} — tap to run it", at)
    }

    private fun notificationId(scheduleId: String) = "schedule-$scheduleId"

    public companion object {
        public const val MAX_LATE_MS: Long = 2 * 60 * 60 * 1000L
    }
}
