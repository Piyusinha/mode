package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.core.common.AppLog
import kotlinx.coroutines.delay

/**
 * Keeps a running task under iOS's background CPU limit. While Mode drives another app it runs in the background,
 * and iOS kills an app that uses more than 80% CPU over 60 seconds (seen on the phone, 9 Nov). Before each step
 * this looks at how much CPU Mode used over the last minute and, above [limitPercent], pauses just long enough to
 * bring that back under. Nothing is skipped: the next step only starts a little later.
 *
 * @param cpuTimeMs the process's CPU time so far (null: unknown, so no pacing).
 */
public class CpuPacer(
    private val cpuTimeMs: () -> Long?,
    private val now: () -> Long,
    private val limitPercent: Int = 60,
    private val hardPercent: Int = 75,
    private val windowMs: Long = 60_000,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    private var lastResortAt: Long? = null

    /**
     * Waits, if needed, so CPU over the last minute is under [limitPercent]. Returns how long it waited.
     *
     * [lastResort] (the user's idea, used only when pausing isn't enough): when CPU is still at [hardPercent] or
     * more after the pauses, Mode comes to the front for a moment, where iOS's background limit doesn't apply,
     * and the task carries on (the agent reopens the app it was in). At most once a minute.
     */
    public suspend fun pace(lastResort: (suspend () -> Unit)? = null): Long {
        var waited = 0L
        repeat(MAX_ROUNDS) {
            val percent = percent() ?: return waited
            if (percent < limitPercent) return waited
            val (span, used) = lastSpan
            // Idle long enough that used / (span + pause) falls to [TARGET_SHARE] of the limit.
            val pause = (used / (limitPercent / 100.0 * TARGET_SHARE) - span).toLong().coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
            AppLog.log("cpu", "busy: ${percent.toInt()}% CPU over the last ${span / 1000} s; pausing ${pause} ms before the next step")
            sleep(pause)
            waited += pause
        }
        val still = percent() ?: return waited
        val t = now()
        if (lastResort != null && still >= hardPercent && lastResortAt.let { it == null || t - it >= windowMs }) {
            lastResortAt = t
            AppLog.log("cpu", "still ${still.toInt()}% CPU after pausing; bringing Mode to the front for a moment (last resort)")
            runCatching { lastResort() }
        }
        return waited
    }

    private var lastSpan: Pair<Long, Long> = 0L to 0L

    /** CPU over the last minute, in percent; null when unknown or measured over too short a time. */
    private fun percent(): Double? {
        val t = now()
        val cpu = cpuTimeMs() ?: return null
        samples.addLast(t to cpu)
        // Keep one sample at or before the window's start, so the average always covers the last minute.
        while (samples.size > 2 && samples[1].first <= t - windowMs) samples.removeFirst()
        val (t0, cpu0) = samples.first()
        val span = t - t0
        if (span < MIN_SPAN_MS) return null
        lastSpan = span to (cpu - cpu0)
        return (cpu - cpu0) * 100.0 / span
    }

    private companion object {
        const val MIN_SPAN_MS = 5_000L
        const val MIN_PAUSE_MS = 500L
        const val MAX_PAUSE_MS = 5_000L
        const val MAX_ROUNDS = 4
        const val TARGET_SHARE = 0.85
    }
}
