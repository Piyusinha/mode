package com.piyush.mode.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.ActivityBridge
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.VoiceOption
import com.piyush.mode.feature.settings.voice.Voices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

/**
 * The phone's own voice on Android, behind the shared [DeviceVoice]: speaking with text-to-speech, playing a
 * provider's clip, listening live with the speech recognizer (on the device when it can), and recording a
 * 16 kHz WAV for a cloud listener (Deepgram, Soniox) until the user stops talking.
 */
class AndroidDeviceVoice(context: Context, private val words: VoiceWords) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(app) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    private val speaking = mutableMapOf<String, (Boolean) -> Unit>()
    /** The lines whose sound has begun: a line stopped part-way was still said (only one that never sounded failed). */
    private val sounding = mutableSetOf<String>()
    private var player: MediaPlayer? = null
    /** The clip playing now waits on this: stopping it must answer, or its caller waits forever. Main thread only. */
    private var clipDone: ((Boolean) -> Unit)? = null
    private val stream = StreamOut()
    private var recognizer: SpeechRecognizer? = null
    private var recording: Job? = null
    @Volatile private var stopRecording = false
    /** The recognizer's own beeps, kept quiet while Mode listens (Google's own voice search makes none either). */
    private val earcons = Earcons(app, main)

    /** The on-device recognizer has the language's pack (false once it said it hasn't, until Mode restarts). */
    private var onDeviceReady = true

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                synchronized(speaking) { if (utteranceId in speaking) sounding += utteranceId }
                VoiceLevel.speaking.value = true
            }
            override fun onDone(utteranceId: String) = finishSpeaking(utteranceId, true)
            @Deprecated("Android still calls it") override fun onError(utteranceId: String) = finishSpeaking(utteranceId, false)
            // Stopped part-way (the user talked over it, or Stop): said, as far as it got.
            override fun onStop(utteranceId: String, interrupted: Boolean) = finishSpeaking(utteranceId, synchronized(speaking) { utteranceId in sounding })
        })
    }

    /** Hands the shared voice code everything it can do on this phone. */
    fun install() {
        // A Mode that stopped mid-listen (killed, crashed) gives back the sounds it had quieted.
        earcons.restoreLeftovers()
        Voices.installReader { path -> File(path).takeIf { it.exists() }?.readBytes() }
        DeviceVoice.deviceLanguages = { listOf(Locale.getDefault().toLanguageTag()) }
        DeviceVoice.deviceInstalled = { listOf(Locale.getDefault().toLanguageTag()) }
        // The recognizer would hear Mode's own voice from the speaker: speak, then listen.
        DeviceVoice.hearsOverSpeech = false
        DeviceVoice.install(
            speechReady = { ttsReady },
            hearingState = ::hearingState,
            speak = ::speak,
            voices = ::voices,
            finish = { stopRecording = true; main.post { recognizer?.stopListening() } },
            play = ::play,
            record = { maxMs, silenceMs, done -> record(maxMs, silenceMs) { path, _ -> done(path) } },
            // The cloud listener transcribes the recording; the phone listens live instead (listen).
            transcribe = { _, done -> done(null) },
            deleteRecording = { path -> File(path).delete() },
            cancel = ::cancel,
            prepare = { scope.launch { ActivityBridge.requestPermission(Manifest.permission.RECORD_AUDIO) } },
            listen = ::listen,
            hush = ::hush,
        )
        // Speechify streams: each chunk plays as it arrives.
        DeviceVoice.streamBegin = stream::begin
        DeviceVoice.streamChunk = stream::chunk
        DeviceVoice.streamEnd = stream::end
    }

    private fun micAllowed() = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** ready, permission, denied or unsupported (Android has no separate download step here). */
    private fun hearingState(): String = when {
        !SpeechRecognizer.isRecognitionAvailable(app) -> "unsupported"
        micAllowed() -> "ready"
        app.getSharedPreferences(ASKED_PREFS, Context.MODE_PRIVATE).getBoolean(Manifest.permission.RECORD_AUDIO, false) -> "denied"
        else -> "permission"
    }

    private fun speak(text: String, voiceId: String?, speed: Double, done: (Boolean) -> Unit) {
        if (!ttsReady) return done(false)
        earcons.loudNow()
        tts.voices?.firstOrNull { it.name == voiceId }?.let { tts.voice = it }
        tts.setSpeechRate(speed.toFloat())
        val id = UUID.randomUUID().toString()
        // QUEUE_FLUSH drops whatever was still being said: those lines are over.
        finishAllSpeaking()
        synchronized(speaking) { speaking[id] = done }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finishSpeaking(id, false)
    }

    /**
     * Stops speaking, whoever speaks (the phone's voice, a clip, a stream): every line still being said ends now
     * (not every engine reports a stopped line), and each caller hears back, so nothing waits on a line that's over.
     */
    private fun hush() {
        tts.stop()
        finishAllSpeaking()
        stream.stop()
        main.post { endClip(true) }
    }

    /** Every line still being said is over: one whose sound had begun counts as said, one that never sounded doesn't. */
    private fun finishAllSpeaking() {
        val pending = synchronized(speaking) {
            speaking.map { (id, done) -> done to (id in sounding) }.also { speaking.clear(); sounding.clear() }
        }
        if (pending.isEmpty()) return
        VoiceLevel.speaking.value = false
        main.post { pending.forEach { (done, said) -> done(said) } }
    }

    private fun finishSpeaking(id: String, ok: Boolean) {
        VoiceLevel.speaking.value = false
        val done = synchronized(speaking) { sounding.remove(id); speaking.remove(id) } ?: return
        main.post { done(ok) }
    }

    /**
     * The phone's voices for the user's language (and English): the user's own accent first, offline before
     * online, named "English (India) · Voice 2" since the engine's names are codes. A "-language" entry is the
     * engine's default for that accent and repeats one of the others, so it is left out.
     */
    private fun voices(): List<VoiceOption> {
        if (!ttsReady) return emptyList()
        val here = Locale.getDefault()
        val all = tts.voices.orEmpty()
            .filter { it.locale.language == here.language || it.locale.language == Locale.ENGLISH.language }
            .filterNot { it.name.endsWith(DEFAULT_SUFFIX) || it.isNetworkConnectionRequired && it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ it.locale != here }, { it.locale.language != here.language }, { it.locale.getDisplayName(here) }, { it.isNetworkConnectionRequired }, { it.name }))
        val counts = mutableMapOf<Locale, Int>()
        return all.map { v ->
            val n = counts.merge(v.locale, 1, Int::plus) ?: 1
            VoiceOption(
                id = v.name,
                name = words.name(v.locale.getDisplayName(here), n),
                traits = if (v.isNetworkConnectionRequired) words.online else words.offline,
                group = v.locale.getDisplayLanguage(here),
            )
        }
    }

    private fun play(bytes: ByteArray, done: (Boolean) -> Unit) {
        earcons.loudNow()
        val file = File(app.cacheDir, CLIP).apply { writeBytes(bytes) }
        main.post {
            // A clip still playing is over now: its caller must not wait forever.
            endClip(true)
            runCatching {
                val p = MediaPlayer()
                player = p
                p.setDataSource(file.absolutePath)
                p.setOnCompletionListener { if (player === p) endClip(true) }
                p.setOnErrorListener { _, _, _ -> if (player === p) endClip(false); true }
                p.prepare()
                p.start()
                clipDone = done
                VoiceLevel.speaking.value = true
            }.onFailure {
                player?.release()
                player = null
                done(false)
            }
        }
    }

    /** The clip is over (played, failed or stopped): the player goes and its caller hears back. Main thread only. */
    private fun endClip(ok: Boolean) {
        val done = clipDone
        clipDone = null
        player?.release()
        player = null
        if (done != null) {
            VoiceLevel.speaking.value = false
            done(ok)
        }
    }

    /**
     * A line that arrives as raw 16-bit mono PCM while it is being made (Speechify): each chunk plays as it comes.
     * Chunks are written on a thread of their own (a full buffer makes the write wait), and the line is over once
     * everything written has been heard, or it is stopped.
     */
    private inner class StreamOut {
        private val lock = Any()
        private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "mode-voice-stream").apply { isDaemon = true } }
        private var track: AudioTrack? = null
        private var done: ((Boolean) -> Unit)? = null
        /** Goes up with every line: work queued for an older line does nothing. */
        private var line = 0
        private var rate = 0
        private var frames = 0L
        private var started = false

        /** Starts a line; false when the phone can't play this format. [done] says, once, whether sound started. */
        fun begin(sampleRate: Int, done: (Boolean) -> Unit): Boolean {
            stop()
            earcons.loudNow()
            val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return false
            val made = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(min, sampleRate * 2 / STREAM_BUFFER_PER_SECOND))
                    .build()
            }.getOrNull()?.takeIf { it.state == AudioTrack.STATE_INITIALIZED }
            if (made == null) {
                AppLog.log("voice", "the phone can't play a $sampleRate Hz stream")
                return false
            }
            synchronized(lock) {
                line++
                track = made
                this.done = done
                rate = sampleRate
                frames = 0
                started = false
            }
            // Sound from the first chunk on: by default a track waits for a full buffer, and a short line ("On it.")
            // that never fills one was never heard.
            runCatching { made.setStartThresholdInFrames((sampleRate / STREAM_START_PER_SECOND).coerceAtLeast(1)) }
            runCatching { made.play() }
            return true
        }

        fun chunk(bytes: ByteArray) {
            val (t, current) = synchronized(lock) { (track ?: return) to line }
            worker.execute {
                var offset = 0
                while (offset < bytes.size && current(current)) {
                    val n = runCatching { t.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING) }.getOrDefault(-1)
                    if (n < 0) return@execute
                    if (n == 0) { Thread.sleep(STREAM_POLL_MS); continue }
                    offset += n
                    synchronized(lock) {
                        if (line != current) return@execute
                        frames += n / 2
                        if (!started) { started = true; VoiceLevel.speaking.value = true }
                    }
                }
            }
        }

        /** No more audio: the line ends once what was written has been heard. */
        fun end() {
            val (t, current) = synchronized(lock) { (track ?: return) to line }
            worker.execute {
                val (total, hz) = synchronized(lock) { if (line != current) return@execute; frames to rate }
                val giveUp = SystemClock.elapsedRealtime() + total * MS / hz.coerceAtLeast(1) + STREAM_TAIL_MS
                while (current(current) && (t.playbackHeadPosition.toLong() and HEAD_MASK) < total && SystemClock.elapsedRealtime() < giveUp) {
                    Thread.sleep(STREAM_POLL_MS)
                }
                finish(current)
            }
        }

        /** Stops the line at once (the user talked over Mode, or it was cancelled). */
        fun stop() = finish(null)

        private fun current(n: Int): Boolean = synchronized(lock) { line == n }

        private fun finish(only: Int?) {
            val (t, callback, said) = synchronized(lock) {
                val callback = done ?: return
                if (only != null && only != line) return
                line++
                done = null
                Triple(track, callback, started).also { track = null }
            }
            VoiceLevel.speaking.value = false
            // Quiet now; the track is let go on its own thread, after any write to it has seen the line is over.
            runCatching { t?.pause() }
            worker.execute { runCatching { t?.flush(); t?.release() } }
            main.post { callback(said) }
        }
    }

    /**
     * One listen: live with the speech recognizer (words as they come), or a recording for a cloud listener.
     * Done gets the recording's path, the phone's final text, and why it stopped.
     */
    private fun listen(maxMs: Long, silenceMs: Long, live: Boolean, onPartial: (String) -> Unit, done: (String?, String?, String) -> Unit) {
        if (!micAllowed()) return done(null, null, FAILED)
        if (!live) return record(maxMs, silenceMs) { path, reason -> done(path, null, reason) }
        main.post { recognize(maxMs, silenceMs, onDevice = onDeviceReady && SpeechRecognizer.isOnDeviceRecognitionAvailable(app), onPartial, done) }
    }

    /**
     * On the phone when its speech pack for the language is there; otherwise the phone's regular recognizer, while
     * Android downloads the pack for next time.
     */
    private fun recognize(maxMs: Long, silenceMs: Long, onDevice: Boolean, onPartial: (String) -> Unit, done: (String?, String?, String) -> Unit) {
        run {
            recognizer?.destroy()
            // The phone's own recognizer by name: as the assistant, Mode's forwarding one is the default.
            val r = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            else SpeechRecognizer.createSpeechRecognizer(app, com.piyush.mode.app.assistant.PhoneRecognizer.component(app))
            recognizer = r
            var finished = false
            var heard = false
            VoiceLevel.listening()
            val quiet = Runnable { if (!heard) VoiceLevel.cantHear.value = true }
            main.postDelayed(quiet, CANT_HEAR_MS)
            fun end(text: String?, reason: String) {
                if (finished) return
                finished = true
                earcons.loudAgain()
                main.removeCallbacks(quiet)
                VoiceLevel.stopped()
                done(null, text?.takeIf { it.isNotBlank() }, reason)
                r.destroy()
                if (recognizer === r) recognizer = null
            }
            val timeout = Runnable { r.stopListening() }
            main.postDelayed(timeout, maxMs)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    VoiceLevel.level.value = ((rmsdB - RMS_FLOOR_DB) / RMS_RANGE_DB).coerceIn(0f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partial: Bundle) {
                    // A listen that was cancelled or replaced says nothing more.
                    if (finished || recognizer !== r) return
                    heard = true
                    VoiceLevel.cantHear.value = false
                    partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
                }
                override fun onResults(results: Bundle) {
                    main.removeCallbacks(timeout)
                    end(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(), SETTLED)
                }
                override fun onError(error: Int) {
                    main.removeCallbacks(timeout)
                    if (onDevice && !heard && (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)) {
                        AppLog.log("voice", "no speech pack on the phone yet; listening with the regular recognizer")
                        onDeviceReady = false
                        finished = true
                        earcons.loudAgain()
                        main.removeCallbacks(quiet)
                        r.destroy()
                        if (recognizer === r) recognizer = null
                        runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(app).triggerModelDownload(intentFor(silenceMs)) }
                        recognize(maxMs, silenceMs, onDevice = false, onPartial, done)
                        return
                    }
                    end(null, if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) QUIET else FAILED)
                }
            })
            earcons.quiet()
            r.startListening(intentFor(silenceMs))
        }
    }

    private fun intentFor(silenceMs: Long): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, DeviceVoice.listenLanguage ?: Locale.getDefault().toLanguageTag())

    /** Records 16 kHz mono until a pause after speech, the limit, Done or cancel; a WAV file, or null if nobody spoke. */
    @SuppressLint("MissingPermission")
    private fun record(maxMs: Long, silenceMs: Long, done: (String?, String) -> Unit) {
        if (!micAllowed()) return done(null, FAILED)
        stopRecording = false
        recording?.cancel()
        recording = scope.launch {
            val size = maxOf(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), RATE / 10 * 2)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            val echo = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null
            val file = File(app.cacheDir, "listen-${System.currentTimeMillis()}.wav")
            var reason = LIMIT
            var spoke = false
            RandomAccessFile(file, "rw").use { out ->
                out.write(ByteArray(WAV_HEADER))
                val buffer = ShortArray(size / 2)
                val started = System.currentTimeMillis()
                var lastVoice = started
                VoiceLevel.listening()
                record.startRecording()
                while (isActive) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) break
                    val bytes = ByteArray(n * 2)
                    var energy = 0.0
                    for (i in 0 until n) {
                        val v = buffer[i].toInt()
                        bytes[i * 2] = (v and 0xFF).toByte()
                        bytes[i * 2 + 1] = (v shr 8 and 0xFF).toByte()
                        energy += v.toDouble() * v
                    }
                    out.write(bytes)
                    val now = System.currentTimeMillis()
                    val rms = sqrt(energy / n)
                    VoiceLevel.level.value = (rms / LOUD).toFloat().coerceIn(0f, 1f)
                    if (rms > SPEECH_LEVEL) {
                        spoke = true
                        lastVoice = now
                        VoiceLevel.cantHear.value = false
                    } else if (!spoke && now - started > CANT_HEAR_MS) {
                        VoiceLevel.cantHear.value = true
                    }
                    when {
                        stopRecording -> { reason = DONE; break }
                        spoke && now - lastVoice > silenceMs -> { reason = QUIET; break }
                        now - started > maxMs -> break
                    }
                }
                runCatching { record.stop() }
                VoiceLevel.stopped()
                writeHeader(out)
            }
            record.release()
            echo?.release()
            if (!isActive) reason = CANCELLED
            AppLog.log("voice", "recorded: $reason, spoke=$spoke")
            val path = if (spoke && reason != CANCELLED) file.absolutePath else null.also { file.delete() }
            main.post { done(path, reason) }
        }
    }

    private fun writeHeader(out: RandomAccessFile) {
        val data = (out.length() - WAV_HEADER).toInt()
        fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        out.seek(0)
        out.write("RIFF".toByteArray()); out.write(le32(data + WAV_HEADER - 8)); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); out.write(le32(16)); out.write(le16(1)); out.write(le16(1))
        out.write(le32(RATE)); out.write(le32(RATE * 2)); out.write(le16(2)); out.write(le16(16))
        out.write("data".toByteArray()); out.write(le32(data))
    }

    private fun cancel() {
        hush()
        VoiceLevel.stopped()
        VoiceLevel.speaking.value = false
        recording?.cancel()
        // The listen being cancelled is the one running now: a new one may start before this runs on the main
        // thread, and must not be the one cancelled.
        val current = recognizer
        recognizer = null
        main.post {
            current?.cancel()
            current?.destroy()
            if (current != null) earcons.loudAgain()
        }
    }

    private companion object {
        const val RATE = 16_000
        const val WAV_HEADER = 44
        /** Root-mean-square level above which the microphone hears speech, not room noise. */
        const val SPEECH_LEVEL = 900.0
        /** A loud voice's level, for the bars. */
        const val LOUD = 4000.0
        /** The recognizer's loudness runs about -2…10 dB. */
        const val RMS_FLOOR_DB = -2f
        const val RMS_RANGE_DB = 12f
        /** Listening this long with nothing heard: talking mode asks whether the microphone is on. */
        const val CANT_HEAR_MS = 6_000L
        const val CLIP = "voice-clip"
        /** A streamed line: a quarter second of buffer, how often its thread looks again, and how long past its length it may take. */
        const val STREAM_BUFFER_PER_SECOND = 4
        /** It starts sounding once a fiftieth of a second has arrived. */
        const val STREAM_START_PER_SECOND = 50
        const val STREAM_POLL_MS = 10L
        const val STREAM_TAIL_MS = 1_500L
        const val MS = 1000L
        /** The playback head is an unsigned 32-bit count of frames. */
        const val HEAD_MASK = 0xFFFFFFFFL
        const val ASKED_PREFS = "mode.permissions"
        const val DEFAULT_SUFFIX = "-language"
        const val QUIET = "quiet"
        const val SETTLED = "settled"
        const val LIMIT = "limit"
        const val DONE = "done"
        const val CANCELLED = "cancelled"
        const val FAILED = "failed"
    }
}

/**
 * Android's speech recognizer beeps when it starts and stops listening; Google's own voice search plays none. While
 * Mode listens, the sounds those beeps use (media, system, notifications) are muted, and a moment after it stops (the
 * closing beep plays then) they come back. Only what Mode muted is unmuted: a stream the user had muted stays so.
 * What was muted is written down, so a Mode killed mid-listen gives it back the next time it starts.
 */
private class Earcons(context: Context, private val main: Handler) {
    private val audio = context.getSystemService(android.media.AudioManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var muted: Set<Int> = emptySet()
    private val restore = Runnable { unmute() }

    /** Main thread. */
    fun quiet() {
        main.removeCallbacks(restore)
        val now = STREAMS.filter { stream -> stream !in muted && runCatching { !audio.isStreamMute(stream) }.getOrDefault(false) }
            .filter { stream -> runCatching { audio.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_MUTE, 0); true }.getOrDefault(false) }
        muted = muted + now
        prefs.edit().putString(KEY, muted.joinToString(",")).apply()
    }

    /** Main thread: the closing beep plays as listening stops, so the sound comes back a moment after. */
    fun loudAgain() {
        main.removeCallbacks(restore)
        main.postDelayed(restore, AFTER_MS)
    }

    /** Mode is about to speak: its own voice must be heard, now. Any thread. */
    fun loudNow() {
        main.post {
            main.removeCallbacks(restore)
            unmute()
        }
    }

    fun restoreLeftovers() {
        muted = prefs.getString(KEY, null)?.split(',')?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty()
        unmute()
    }

    private fun unmute() {
        muted.forEach { stream -> runCatching { audio.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_UNMUTE, 0) } }
        muted = emptySet()
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        val STREAMS = listOf(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.STREAM_NOTIFICATION)
        const val AFTER_MS = 700L
        const val PREFS = "mode.voice"
        const val KEY = "earcons.muted"
    }
}

/** How the phone's voices are named in the picker, from the app's strings. */
class VoiceWords(val name: (accent: String, number: Int) -> String, val offline: String, val online: String)

/** The microphone's loudness (0…1), whether Mode is speaking, and whether it hears nothing: talking mode's orb and bars. */
object VoiceLevel {
    val level = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val speaking = kotlinx.coroutines.flow.MutableStateFlow(false)
    val cantHear = kotlinx.coroutines.flow.MutableStateFlow(false)

    internal fun listening() {
        level.value = 0f
        cantHear.value = false
    }

    internal fun stopped() {
        level.value = 0f
        cantHear.value = false
    }
}
