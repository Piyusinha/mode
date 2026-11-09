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
    private val windowMs: Long = 60_000,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    /** Waits, if needed, so CPU over the last minute is under [limitPercent]. Returns how long it waited. */
    public suspend fun pace(): Long {
        var waited = 0L
        repeat(MAX_ROUNDS) {
            val t = now()
            val cpu = cpuTimeMs() ?: return waited
            samples.addLast(t to cpu)
            // Keep one sample at or before the window's start, so the average always covers the last minute.
            while (samples.size > 2 && samples[1].first <= t - windowMs) samples.removeFirst()
            val (t0, cpu0) = samples.first()
            val span = t - t0
            if (span < MIN_SPAN_MS) return waited
            val used = cpu - cpu0
            val percent = used * 100.0 / span
            if (percent < limitPercent) return waited
            // Idle long enough that used / (span + pause) falls to [TARGET_SHARE] of the limit.
            val pause = (used / (limitPercent / 100.0 * TARGET_SHARE) - span).toLong().coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
            AppLog.log("cpu", "busy: ${percent.toInt()}% CPU over the last ${span / 1000} s; pausing ${pause} ms before the next step")
            sleep(pause)
            waited += pause
        }
        return waited
    }

    private companion object {
        const val MIN_SPAN_MS = 5_000L
        const val MIN_PAUSE_MS = 500L
        const val MAX_PAUSE_MS = 5_000L
        const val MAX_ROUNDS = 4
        const val TARGET_SHARE = 0.85
    }
}
