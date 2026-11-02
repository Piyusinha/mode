package com.piyush.mode.feature.settings.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * What only the phone can do: Apple's voice, playing a clip, and recording once.
 * The app installs the real one at launch. Until then every call is "not ready".
 */
public object DeviceVoice {
    public var speechReady: () -> Boolean = { false }
    /** ready, permission, denied, unsupported, or asset. */
    public var hearingState: () -> String = { "unsupported" }
    public var speak: ((String, (Boolean) -> Unit) -> Unit)? = null
    public var play: ((ByteArray, (Boolean) -> Unit) -> Unit)? = null
    /** A file path, or null when nobody spoke or the user cancelled. */
    public var record: ((Long, Long, (String?) -> Unit) -> Unit)? = null
    public var transcribe: ((String, (String?) -> Unit) -> Unit)? = null
    public var deleteRecording: (String) -> Unit = {}
    public var cancel: () -> Unit = {}

    /** The app installs the phone's voice once, at launch. */
    public fun install(
        speechReady: () -> Boolean,
        hearingState: () -> String,
        speak: (String, (Boolean) -> Unit) -> Unit,
        play: (ByteArray, (Boolean) -> Unit) -> Unit,
        record: (Long, Long, (String?) -> Unit) -> Unit,
        transcribe: (String, (String?) -> Unit) -> Unit,
        deleteRecording: (String) -> Unit,
        cancel: () -> Unit,
    ) {
        this.speechReady = speechReady
        this.hearingState = hearingState
        this.speak = speak
        this.play = play
        this.record = record
        this.transcribe = transcribe
        this.deleteRecording = deleteRecording
        this.cancel = cancel
    }

    public suspend fun speakLine(text: String): Boolean = await { done -> speak?.invoke(text, done) ?: done(false) }

    public suspend fun playClip(bytes: ByteArray): Boolean = await { done -> play?.invoke(bytes, done) ?: done(false) }

    public suspend fun recordOnce(maxMs: Long, silenceMs: Long): String? = awaitNullable { done ->
        record?.invoke(maxMs, silenceMs, done) ?: done(null)
    }

    public suspend fun transcribeApple(path: String): String? = awaitNullable { done ->
        transcribe?.invoke(path, done) ?: done(null)
    }

    private suspend fun await(start: ((Boolean) -> Unit) -> Unit): Boolean = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        start { ok -> if (cont.isActive) cont.resume(ok) }
    }

    private suspend fun awaitNullable(start: ((String?) -> Unit) -> Unit): String? = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        start { value -> if (cont.isActive) cont.resume(value) }
    }
}
