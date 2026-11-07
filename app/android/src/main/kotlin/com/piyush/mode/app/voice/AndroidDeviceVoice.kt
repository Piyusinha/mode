package com.piyush.mode.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var player: MediaPlayer? = null
    private var recognizer: SpeechRecognizer? = null
    private var recording: Job? = null
    @Volatile private var stopRecording = false
    /** The on-device recognizer has the language's pack (false once it said it hasn't, until Mode restarts). */
    private var onDeviceReady = true

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) { VoiceLevel.speaking.value = true }
            override fun onDone(utteranceId: String) = finishSpeaking(utteranceId, true)
            @Deprecated("Android still calls it") override fun onError(utteranceId: String) = finishSpeaking(utteranceId, false)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finishSpeaking(utteranceId, false)
        })
    }

    /** Hands the shared voice code everything it can do on this phone. */
    fun install() {
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
            hush = { tts.stop() },
        )
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
        tts.voices?.firstOrNull { it.name == voiceId }?.let { tts.voice = it }
        tts.setSpeechRate(speed.toFloat())
        val id = UUID.randomUUID().toString()
        synchronized(speaking) { speaking[id] = done }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finishSpeaking(id, false)
    }

    private fun finishSpeaking(id: String, ok: Boolean) {
        VoiceLevel.speaking.value = false
        val done = synchronized(speaking) { speaking.remove(id) } ?: return
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
        val file = File(app.cacheDir, CLIP).apply { writeBytes(bytes) }
        main.post {
            runCatching {
                player?.release()
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { VoiceLevel.speaking.value = false; done(true); it.release(); player = null }
                    setOnErrorListener { mp, _, _ -> VoiceLevel.speaking.value = false; done(false); mp.release(); player = null; true }
                    prepare()
                    start()
                    VoiceLevel.speaking.value = true
                }
            }.onFailure { done(false) }
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
        tts.stop()
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
            player?.release()
            player = null
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
