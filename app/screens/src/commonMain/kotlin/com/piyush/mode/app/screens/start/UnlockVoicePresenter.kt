package com.piyush.mode.app.screens.start

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.DeviceLock
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.feature.settings.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** Where the "Try it" check is. */
public sealed interface UnlockTest {
    /** Not testing. */
    public data object Idle : UnlockTest

    /** Waiting for the user to lock the iPhone. */
    public data class WaitingForLock(val secondsLeft: Int) : UnlockTest

    /** Locked: waiting for the phrase to unlock it. */
    public data class WaitingForUnlock(val secondsLeft: Int) : UnlockTest

    /** It unlocked [seconds] after locking. */
    public data class Works(val seconds: Int) : UnlockTest

    /** The iPhone was never locked in time. */
    public data object NeverLocked : UnlockTest

    /** It stayed locked: the phrase didn't unlock it. */
    public data object StayedLocked : UnlockTest
}

public data class UnlockVoiceUiState(
    /** The user said they set it up (or a test passed). */
    val setUp: Boolean = false,
    val test: UnlockTest = UnlockTest.Idle,
)

/**
 * Mo tab → Start Mo → Unlock by voice. The unlock is the user's own iOS Voice Control command: Mode only
 * guides and checks it. [setUp] remembers that they made it; [startTest] watches the lock (Mode stays
 * awake): the iPhone must lock within [LOCK_WAIT_S], then unlock within [UNLOCK_WAIT_S]. Mode never
 * sees or keeps the phrase or the passcode.
 */
public class UnlockVoicePresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val lock: DeviceLock,
    private val background: BackgroundWork? = null,
    private val telemetry: Telemetry = Telemetry.None,
) : Presenter<UnlockVoiceUiState>(parent, UnlockVoiceUiState(setUp = settings.settings.value.voiceUnlock)) {
    private var test: Job? = null

    init {
        scope.launch {
            settings.settings.map { it.voiceUnlock }.collect { on -> mutableState.value = mutableState.value.copy(setUp = on) }
        }
    }

    /** "I've set it up": remember it, and start checking that it works. */
    public fun setUp() {
        settings.update { it.copy(voiceUnlock = true) }
        startTest()
    }

    /** "Turn off": Mode stops mentioning the phrase. (The command itself stays in iOS until the user deletes it.) */
    public fun turnOff() {
        cancelTest()
        settings.update { it.copy(voiceUnlock = false) }
    }

    public fun startTest() {
        test?.cancel()
        test = scope.launch {
            val work: suspend () -> Unit = { runTest() }
            background?.run(work) ?: work()
        }
    }

    public fun cancelTest() {
        test?.cancel()
        test = null
        set(UnlockTest.Idle)
    }

    private suspend fun runTest() {
        AppLog.log("unlock", "test started")
        // Already locked (the screen opened from a Lock Screen tap): wait for it to unlock first.
        if (lock.locked.value) lock.locked.first { !it }
        val locked = countdown(LOCK_WAIT_S, { UnlockTest.WaitingForLock(it) }) { lock.locked.first { it } }
        if (locked == null) return finish(UnlockTest.NeverLocked, "never_locked")
        val at = TimeSource.Monotonic.markNow()
        val unlocked = countdown(UNLOCK_WAIT_S, { UnlockTest.WaitingForUnlock(it) }) { lock.locked.first { !it } }
        if (unlocked == null) return finish(UnlockTest.StayedLocked, "stayed_locked")
        val seconds = at.elapsedNow().inWholeSeconds.toInt().coerceAtLeast(1)
        settings.update { it.copy(voiceUnlock = true) }
        finish(UnlockTest.Works(seconds), "works")
    }

    /** Runs [until], showing the seconds left each second; null when time ran out. */
    private suspend fun countdown(seconds: Int, show: (Int) -> UnlockTest, until: suspend () -> Unit): Unit? = coroutineScopeOf {
        val ticker = launch {
            for (left in seconds downTo 1) {
                set(show(left))
                delay(1_000)
            }
        }
        try {
            withTimeoutOrNull(seconds * 1_000L) { until() }
        } finally {
            ticker.cancel()
        }
    }

    private fun finish(result: UnlockTest, outcome: String) {
        AppLog.log("unlock", "test: $outcome")
        telemetry.event(com.piyush.mode.core.telemetry.Event.UnlockTest, fields = mapOf(com.piyush.mode.core.telemetry.Field.Outcome to outcome))
        set(result)
    }

    private fun set(test: UnlockTest) {
        mutableState.value = mutableState.value.copy(test = test)
    }

    public companion object {
        public const val LOCK_WAIT_S: Int = 60
        public const val UNLOCK_WAIT_S: Int = 30
    }
}

private suspend fun <T> coroutineScopeOf(block: suspend CoroutineScope.() -> T): T = kotlinx.coroutines.coroutineScope(block)
