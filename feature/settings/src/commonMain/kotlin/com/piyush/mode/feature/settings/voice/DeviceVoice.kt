package com.piyush.mode.feature.settings.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * What only the phone can do: Apple's voice, playing a clip, and recording once.
 * The app installs the real one at launch. Until then every call is "not ready".
 */
public object DeviceVoice {
    /** The language to listen in (BCP-47, "hi-IN"); null: this iPhone's language. Set before each listen. */
    public var listenLanguage: String? = null
    /** Languages Apple can listen in on this iPhone, and those already downloaded (BCP-47). */
    public var appleLanguages: () -> List<String> = { emptyList() }
    public var appleInstalled: () -> List<String> = { emptyList() }
    public var speechReady: () -> Boolean = { false }
    /** ready, permission, denied, unsupported, or asset. */
    public var hearingState: () -> String = { "unsupported" }
    /** Says a line in a voice (Apple's identifier; null: the system default) at a speed factor (1 = normal). */
    public var speak: ((String, String?, Double, (Boolean) -> Unit) -> Unit)? = null
    /** Apple's voices on this iPhone (Personal Voice too, once allowed). */
    public var voices: () -> List<VoiceOption> = { emptyList() }
    /** Ends the recording now, keeping what was said. */
    public var finish: () -> Unit = {}
    public var play: ((ByteArray, (Boolean) -> Unit) -> Unit)? = null
    /**
     * Records once from the microphone until a pause after speech, the limit, Done or cancel. With live, Apple
     * transcribes as it goes: partial gets the words so far. Done gets the recording's path (null: nobody spoke, or
     * cancelled) and Apple's final text (null when not live or nothing was heard).
     */
    public var listen: ((Long, Long, Boolean, (String) -> Unit, (String?, String?, String) -> Unit) -> Unit)? = null
    /** A file path, or null when nobody spoke or the user cancelled. */
    public var record: ((Long, Long, (String?) -> Unit) -> Unit)? = null
    public var transcribe: ((String, (String?) -> Unit) -> Unit)? = null
    public var deleteRecording: (String) -> Unit = {}
    public var cancel: () -> Unit = {}
    /** Asks for the microphone and installs Apple's speech model: only when the user turns Apple hearing on. */
    public var prepare: () -> Unit = {}

    /** The app installs the phone's voice once, at launch. */
    public fun install(
        speechReady: () -> Boolean,
        hearingState: () -> String,
        speak: (String, String?, Double, (Boolean) -> Unit) -> Unit,
        voices: () -> List<VoiceOption>,
        finish: () -> Unit,
        play: (ByteArray, (Boolean) -> Unit) -> Unit,
        record: (Long, Long, (String?) -> Unit) -> Unit,
        transcribe: (String, (String?) -> Unit) -> Unit,
        deleteRecording: (String) -> Unit,
        cancel: () -> Unit,
        prepare: () -> Unit,
        listen: (Long, Long, Boolean, (String) -> Unit, (String?, String?, String) -> Unit) -> Unit,
    ) {
        this.speechReady = speechReady
        this.hearingState = hearingState
        this.speak = speak
        this.voices = voices
        this.finish = finish
        this.play = play
        this.record = record
        this.transcribe = transcribe
        this.deleteRecording = deleteRecording
        this.cancel = cancel
        this.prepare = prepare
        this.listen = listen
    }

    /**
     * What one listen gave: the recording (for a cloud hearer), Apple's live transcription, and why it stopped
     * (quiet, settled, limit, done, cancelled, failed).
     */
    public class Listened(public val path: String?, public val text: String?, public val reason: String? = null)

    public suspend fun listenOnce(maxMs: Long, silenceMs: Long, live: Boolean, onPartial: (String) -> Unit): Listened {
        val start = listen ?: return Listened(recordOnce(maxMs, silenceMs), null)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cancel() }
            start(maxMs, silenceMs, live, onPartial) { path, text, reason -> if (cont.isActive) cont.resume(Listened(path, text, reason)) }
        }
    }

    public suspend fun speakLine(text: String, voiceId: String? = null, speed: Double = 1.0): Boolean =
        await { done -> speak?.invoke(text, voiceId, speed, done) ?: done(false) }

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
