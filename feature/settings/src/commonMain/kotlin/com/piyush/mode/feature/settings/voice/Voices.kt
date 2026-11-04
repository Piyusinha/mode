package com.piyush.mode.feature.settings.voice

import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.Field
import com.piyush.mode.core.telemetry.Event
import kotlinx.coroutines.async
import com.piyush.mode.core.telemetry.trace
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.VoiceBlock
import com.piyush.mode.feature.settings.domain.VoiceKind
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import com.piyush.mode.feature.settings.domain.VoiceChoice
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * One voice a provider offers: its [id] there, a name, what it sounds like ("Female · English (India) · Premium"),
 * the [group] it is filtered by (a language or an accent), a [preview] clip URL when the provider has one, and a
 * short [tag] ("Free download", "Popular").
 */
public data class VoiceOption(
    val id: String,
    val name: String,
    val traits: String = "",
    val group: String? = null,
    val preview: String? = null,
    val tag: String? = null,
)

/** A provider's voices, or why they could not be listed (no key yet, refused). */
public data class VoiceList(val options: List<VoiceOption>, val error: VoiceListError? = null)

public enum class VoiceListError { NoKey, Failed }

/** True when sound has started. A later failure must not say the line again. */
public enum class Spoken { Started, NotStarted }

/** What one listen produced. Silence means do not try another provider. */
public sealed interface Heard {
    public data object Silence : Heard
    public data class Words(val text: String) : Heard
    public data object Nothing : Heard
}

/** Why the chosen provider could not speak or hear. The app says so, with a button to fix it in Settings. */
public enum class VoiceTrouble {
    /** No key saved for it. */
    NoKey,
    /** The provider refused the key (401). */
    KeyRejected,
    /** The provider's plan doesn't allow it, or its allowance ran out (402, 403, 429). */
    PlanLimit,
    /** No answer from the provider (no internet, a time-out, a server error). */
    Unreachable,
    /** Apple can't speak or listen on this iPhone yet (microphone off, speech model missing). */
    NotReady,
}

/** The chosen [kind] failed to speak ([hearing] false) or to hear. */
public data class VoiceProblem(val kind: VoiceKind, val hearing: Boolean, val trouble: VoiceTrouble)

/** Whether a pasted key works, asked of the provider before it is saved. */
public sealed interface KeyCheck {
    public data object Valid : KeyCheck
    public data class Rejected(val trouble: VoiceTrouble) : KeyCheck
    public data object Unreachable : KeyCheck
}

/**
 * Speaks and hears with the one provider the user chose for each (the first in the saved order). No
 * quiet fallback: when the chosen provider fails, [problem] says why and the line is not said by another
 * voice, so the user always hears the voice they picked or is told what to fix.
 */
public class Voices(
    private val settings: SettingsRepository,
    private val http: HttpClient,
    private val device: DeviceVoice = DeviceVoice,
    private val telemetry: Telemetry = Telemetry.None,
    /** For the moment speech started while Mode speaks (tests pass a virtual one). */
    private val timeSource: kotlin.time.TimeSource = kotlin.time.TimeSource.Monotonic,
) {
    private val troubles = kotlinx.coroutines.flow.MutableStateFlow<VoiceProblem?>(null)

    /** The last time the chosen speaking or listening provider failed; cleared when it next works. */
    public val problem: kotlinx.coroutines.flow.StateFlow<VoiceProblem?> = troubles

    /** Reset Mode: no provider is chosen any more, so no failure stands. */
    public fun clearProblem() { troubles.value = null }

    private fun failed(kind: VoiceKind, hearing: Boolean, trouble: VoiceTrouble) {
        troubles.value = VoiceProblem(kind, hearing, trouble)
        AppLog.log("voice", "${kind.id} ${if (hearing) "hearing" else "speaking"} failed: $trouble")
    }

    private fun worked(hearing: Boolean) {
        if (troubles.value?.hearing == hearing) troubles.value = null
    }

    /** The provider chosen for speaking or hearing. */
    public fun chosen(hearing: Boolean): VoiceKind = order(hearing).firstOrNull() ?: VoiceKind.Apple

    private fun blockTrouble(b: VoiceBlock): VoiceTrouble = if (b == VoiceBlock.NoKey) VoiceTrouble.NoKey else VoiceTrouble.NotReady

    public fun block(kind: VoiceKind, hearing: Boolean): VoiceBlock? = when {
        kind.needsKey && settings.voiceKey(kind).isNullOrBlank() -> VoiceBlock.NoKey
        kind == VoiceKind.Apple && !hearing && !device.speechReady() -> VoiceBlock.Unsupported
        kind == VoiceKind.Apple && hearing -> when (device.hearingState()) {
            "ready" -> null
            "denied" -> VoiceBlock.Denied
            "permission" -> VoiceBlock.Permission
            "asset" -> VoiceBlock.Asset
            else -> VoiceBlock.Unsupported
        }
        else -> null
    }

    /** Says [text] with the chosen voice; the span records the provider and whether sound played (Spoke, Blocked, Failed). */
    public suspend fun speak(text: String): Unit = telemetry.trace(Operation.VoiceSpeak) { span -> speakLine(text, span) }

    /**
     * Says [text] with the microphone open, so the user can talk over Mode: the moment their words are heard
     * (BargeIn), Mode stops mid-line and keeps listening to the end of their turn. Their words come back; null
     * when the line was said to its end (or they only nodded). [onPartial] gets the words as they come.
     */
    public suspend fun speakOrHear(text: String, onPartial: (String) -> Unit = {}): Heard? {
        val line = text.trim()
        if (line.isEmpty()) return null
        if (!settings.settings.value.bargeIn || block(VoiceKind.Apple, hearing = true) != null || block(chosen(hearing = true), hearing = true) != null) {
            speak(line); return null
        }
        return telemetry.trace(Operation.VoiceSpeak, mapOf(Field.Entry to "interruptible")) { span -> kotlinx.coroutines.coroutineScope {
            var interrupted = false
            var speechStartedAt: kotlin.time.TimeMark? = null
            val language = com.piyush.mode.feature.settings.domain.SpeechLanguage.fromId(settings.settings.value.language)
            device.listenLanguage = language.takeUnless { it == com.piyush.mode.feature.settings.domain.SpeechLanguage.Auto }?.id
            val listening = async {
                device.listenOnce(MAX_RECORD_MS, SILENCE_MS, live = true) { words ->
                    onPartial(words)
                    if (words.isNotBlank() && speechStartedAt == null) speechStartedAt = timeSource.markNow()
                    val speechMs = speechStartedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L
                    if (!interrupted && BargeIn.shouldInterrupt(words, line, speechMs)) {
                        interrupted = true
                        device.hush()
                        telemetry.event(Event.BargeIn, span.context, mapOf(Field.Words to BargeIn.newWords(words, line).size.toString()))
                    }
                }
            }
            try {
                speakLine(line, span)
            } catch (e: CancellationException) {
                listening.cancel(); throw e
            }
            if (!interrupted) {
                // The line was said to its end: the microphone closes; the caller listens afresh if it wants to.
                listening.cancel()
                return@coroutineScope null
            }
            span.fields(mapOf(Field.Outcome to "Interrupted"))
            val got = listening.await()
            val text = got.text?.trim().orEmpty()
            when {
                text.isNotEmpty() -> Heard.Words(text).also { worked(hearing = true) }
                got.path != null -> hearRecording(got.path, chosen(hearing = true), language)
                else -> Heard.Silence
            }
        } }
    }

    /** A recording without Apple's words: the chosen cloud hearer transcribes it. */
    private suspend fun hearRecording(path: String, kind: VoiceKind, language: com.piyush.mode.feature.settings.domain.SpeechLanguage): Heard = try {
        val result = withTimeoutOrNull(PER_HEAR) {
            when (kind) {
                VoiceKind.Soniox -> soniox(settings.voiceKey(kind)!!, path, language.code)
                VoiceKind.Deepgram -> deepgram(settings.voiceKey(kind)!!, path, language.code)
                else -> Transcript.Text(device.transcribeApple(path))
            }
        }
        val text = (result as? Transcript.Text)?.text?.trim().orEmpty()
        if (text.isEmpty()) Heard.Silence else Heard.Words(text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Heard.Nothing
    } finally {
        device.deleteRecording(path)
    }

    private suspend fun speakLine(text: String, span: com.piyush.mode.core.telemetry.TraceSpan) {
        val line = text.trim()
        if (line.isEmpty()) return span.fields(mapOf(Field.Outcome to "Empty"))
        val kind = chosen(hearing = false)
        span.fields(mapOf(Field.Provider to kind.id))
        block(kind, hearing = false)?.let {
            span.fields(mapOf(Field.Outcome to "Blocked"))
            failed(kind, hearing = false, blockTrouble(it)); return
        }
        val result = try {
            telemetry.trace(Operation.VoiceSynthesize, mapOf(Field.Provider to kind.id)) { span ->
                say(kind, settings.settings.value.voices[kind.id]?.id, line).also {
                    if (it !is Said.Started) span.failed("SpeechNotStarted")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.log("voice", "${kind.id} speech failed before sound: ${e.message}")
            Said.Failed(VoiceTrouble.Unreachable)
        }
        when (result) {
            Said.Started -> { span.fields(mapOf(Field.Outcome to "Spoke")); worked(hearing = false) }
            is Said.Failed -> { span.fields(mapOf(Field.Outcome to "Failed", Field.ErrorType to result.trouble.toString())); failed(kind, hearing = false, result.trouble) }
        }
    }

    private sealed interface Said {
        data object Started : Said
        data class Failed(val trouble: VoiceTrouble) : Said
    }

    /**
     * One recording, then each ready hearer, in order. With Apple ready, its transcription runs live while the
     * user speaks: [onPartial] gets the words so far (the screen shows them), and its final text needs no second
     * pass. The same recording goes to Soniox or Deepgram if Apple heard nothing.
     */
    public suspend fun hear(onPartial: (String) -> Unit = {}): Heard =
        telemetry.trace(Operation.VoiceHear) { listen(onPartial) }

    private suspend fun listen(onPartial: (String) -> Unit): Heard {
        val kind = chosen(hearing = true)
        block(kind, hearing = true)?.let { failed(kind, hearing = true, blockTrouble(it)); return Heard.Nothing }
        // Apple's live words show while the user speaks, whichever provider writes the final text.
        val appleLive = block(VoiceKind.Apple, hearing = true) == null
        val language = com.piyush.mode.feature.settings.domain.SpeechLanguage.fromId(settings.settings.value.language)
        device.listenLanguage = language.takeUnless { it == com.piyush.mode.feature.settings.domain.SpeechLanguage.Auto }?.id
        val heard = telemetry.trace(Operation.VoiceRecord, mapOf(Field.Provider to kind.id)) { span ->
            device.listenOnce(MAX_RECORD_MS, SILENCE_MS, live = appleLive, onPartial).also { got ->
                val words = got.text?.trim()?.split(Regex("\\s+"))?.count { it.isNotEmpty() } ?: 0
                span.fields(buildMap {
                    got.reason?.let { put(Field.EndReason, it) }
                    put(Field.Words, words.toString())
                    put(Field.Outcome, if (got.path == null && words == 0) "Silence" else "Heard")
                })
            }
        }
        if (kind == VoiceKind.Apple && heard.path == null) {
            return if (heard.text.isNullOrBlank()) Heard.Silence else Heard.Words(heard.text.trim()).also { worked(hearing = true) }
        }
        val path = heard.path ?: return Heard.Silence
        return try {
            val result = telemetry.trace(Operation.VoiceTranscribe, mapOf(Field.Provider to kind.id)) { span ->
                (withTimeoutOrNull(PER_HEAR) {
                    when (kind) {
                        VoiceKind.Apple -> Transcript.Text(heard.text ?: device.transcribeApple(path))
                        VoiceKind.Soniox -> soniox(settings.voiceKey(kind)!!, path, language.code)
                        VoiceKind.Deepgram -> deepgram(settings.voiceKey(kind)!!, path, language.code)
                        else -> Transcript.Text(null)
                    }
                } ?: Transcript.Failed(VoiceTrouble.Unreachable)).also {
                    span.fields(mapOf(Field.Outcome to when (it) { is Transcript.Failed -> "Failed"; is Transcript.Text -> if (it.text.isNullOrBlank()) "Empty" else "Words" }))
                }
            }
            when (result) {
                is Transcript.Failed -> { failed(kind, hearing = true, result.trouble); Heard.Nothing }
                is Transcript.Text -> {
                    worked(hearing = true)
                    if (result.text.isNullOrBlank()) Heard.Silence else Heard.Words(result.text.trim())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.log("voice", "${kind.id} transcription failed: ${e.message}")
            failed(kind, hearing = true, VoiceTrouble.Unreachable)
            Heard.Nothing
        } finally {
            device.deleteRecording(path)
        }
    }

    private sealed interface Transcript {
        data class Text(val text: String?) : Transcript
        data class Failed(val trouble: VoiceTrouble) : Transcript
    }

    /**
     * Asks [kind]'s provider whether [key] works, before it is saved: a cheap read that needs a valid key
     * (ElevenLabs' voices, Fish Audio's credit, Deepgram's projects, Soniox's models).
     */
    public suspend fun checkKey(kind: VoiceKind, key: String): KeyCheck {
        val k = key.trim()
        if (k.isEmpty()) return KeyCheck.Rejected(VoiceTrouble.NoKey)
        return try {
            val response = when (kind) {
                VoiceKind.ElevenLabs -> http.get("https://api.elevenlabs.io/v2/voices?page_size=1") { header("xi-api-key", k) }
                VoiceKind.Fish -> http.get("https://api.fish.audio/wallet/self/api-credit") { header("Authorization", "Bearer $k") }
                VoiceKind.Deepgram -> http.get("https://api.deepgram.com/v1/projects") { header("Authorization", "Token $k") }
                VoiceKind.Soniox -> http.get("https://api.soniox.com/v1/models") { header("Authorization", "Bearer $k") }
                VoiceKind.Apple -> return KeyCheck.Valid
            }
            when {
                response.status.isSuccess() -> KeyCheck.Valid
                else -> troubleFor(response.status.value)?.let { KeyCheck.Rejected(it) } ?: KeyCheck.Unreachable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KeyCheck.Unreachable
        }
    }

    /** [line] in [voiceId] (null: the provider's default), at the user's speed. */
    private suspend fun say(kind: VoiceKind, voiceId: String?, line: String): Said {
        val speed = settings.settings.value.speechSpeed.factor
        return when (kind) {
            VoiceKind.Apple -> if (device.speakLine(line, voiceId, speed)) Said.Started else Said.Failed(VoiceTrouble.NotReady)
            VoiceKind.Fish -> cloudSpeak(FISH_TTS, settings.voiceKey(kind)!!, fish = true, line, voiceId, speed)
            VoiceKind.ElevenLabs -> cloudSpeak(elevenUrl(voiceId), settings.voiceKey(kind)!!, fish = false, line, voiceId, speed)
            else -> Said.Failed(VoiceTrouble.NotReady)
        }
    }

    /** The provider chosen for speaking, and the voice picked there. */
    public fun speaker(): Pair<VoiceKind, VoiceChoice?>? = chosen(hearing = false).let { it to settings.settings.value.voices[it.id] }

    /** The provider chosen for hearing. */
    public fun listener(): VoiceKind? = chosen(hearing = true)

    /** The voices [kind] offers: Apple's on this iPhone; ElevenLabs' library and Fish Audio's public voices with a key. */
    public suspend fun options(kind: VoiceKind): VoiceList {
        if (kind == VoiceKind.Apple) return VoiceList(device.voices())
        val key = settings.voiceKey(kind) ?: return VoiceList(emptyList(), VoiceListError.NoKey)
        return try {
            VoiceList(if (kind == VoiceKind.ElevenLabs) elevenVoices(key) else fishVoices(key))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.log("voice", "${kind.id} voices could not be listed: ${e.message}")
            VoiceList(emptyList(), VoiceListError.Failed)
        }
    }

    /** Plays what [option] sounds like: the provider's own clip when it has one, else a sample line. */
    public suspend fun preview(kind: VoiceKind, option: VoiceOption): Boolean = try {
        // A sample that never reports its end still ends: the play button must not stay on "playing".
        withTimeoutOrNull(MAX_PREVIEW) {
            val clip = option.preview?.let { url -> http.get(url).takeIf { it.status.isSuccess() }?.bodyAsBytes()?.takeIf { it.isNotEmpty() } }
            if (clip != null) device.playClip(clip) else say(kind, option.id.ifEmpty { null }, SAMPLE) == Said.Started
        } ?: run { device.cancel(); false }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.log("voice", "${kind.id} preview failed: ${e.message}")
        false
    }

    /** Stops whatever is playing or recording. */
    public fun stop() { device.cancel() }

    /** Ends the recording now (the user tapped Done): what was said so far is heard. */
    public fun finishListening() { device.finish() }

    private suspend fun elevenVoices(key: String): List<VoiceOption> {
        val response = http.get("https://api.elevenlabs.io/v2/voices?page_size=100") { header("xi-api-key", key) }
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        val voices = json(response.bodyAsText())?.get("voices") as? JsonArray ?: return emptyList()
        return voices.mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val id = o["voice_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val labels = o["labels"] as? JsonObject
            fun label(k: String) = labels?.get(k)?.jsonPrimitive?.contentOrNull?.replace('_', ' ')?.trim()?.takeIf { it.isNotEmpty() }
            VoiceOption(
                id = id, name = o["name"]?.jsonPrimitive?.contentOrNull?.substringBefore(" - ")?.trim() ?: id,
                traits = listOfNotNull(label("gender"), label("age"), label("accent"), label("descriptive") ?: label("description"))
                    .joinToString(" · ") { it.replaceFirstChar(Char::uppercase) },
                group = label("use_case")?.replaceFirstChar(Char::uppercase),
                preview = o["preview_url"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private suspend fun fishVoices(key: String): List<VoiceOption> {
        val response = http.get("https://api.fish.audio/model?page_size=40&sort_by=task_count") { header("Authorization", "Bearer $key") }
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        val items = json(response.bodyAsText())?.get("items") as? JsonArray ?: return emptyList()
        return items.mapIndexedNotNull { i, v ->
            val o = v as? JsonObject ?: return@mapIndexedNotNull null
            val id = o["_id"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val languages = (o["languages"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val uses = o["task_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val sample = ((o["samples"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("audio")?.jsonPrimitive?.contentOrNull
            VoiceOption(
                id = id, name = o["title"]?.jsonPrimitive?.contentOrNull?.trim() ?: id,
                traits = (languages.map(::languageName) + listOfNotNull(uses?.let(::usesLabel))).joinToString(" · "),
                group = languages.firstOrNull()?.let(::languageName),
                preview = sample, tag = if (i < 3) POPULAR else null,
            )
        }
    }

    private fun order(hearing: Boolean): List<VoiceKind> {
        val ids = if (hearing) settings.settings.value.hearingOrder else settings.settings.value.speechOrder
        return ids.mapNotNull(VoiceKind::fromId)
    }

    private suspend fun cloudSpeak(url: String, key: String, fish: Boolean, text: String, voiceId: String?, speed: Double): Said {
        val response = http.post(url) {
            if (fish) {
                header("Authorization", "Bearer $key")
                header("model", "s1")
            } else {
                header("xi-api-key", key)
            }
            setBody(TextContent(speechBody(fish, text, voiceId, speed), ContentType.Application.Json))
        }
        if (!response.status.isSuccess()) {
            AppLog.log("voice", "speech ${response.status.value}")
            return Said.Failed(troubleFor(response.status.value) ?: VoiceTrouble.Unreachable)
        }
        val bytes = response.bodyAsBytes()
        if (bytes.isEmpty()) return Said.Failed(VoiceTrouble.Unreachable)
        return if (device.playClip(bytes)) Said.Started else Said.Failed(VoiceTrouble.NotReady)
    }

    private fun speechBody(fish: Boolean, text: String, voiceId: String?, speed: Double): String = buildJsonObject {
        put("text", text)
        if (fish) {
            put("format", "mp3")
            voiceId?.let { put("reference_id", it) }
            putJsonObject("prosody") { put("speed", speed) }
        } else {
            put("model_id", "eleven_flash_v2_5")
            putJsonObject("voice_settings") { put("speed", speed) }
        }
    }.toString()

    /** [language]: the two-letter code to listen in; null: Deepgram detects it. */
    private suspend fun deepgram(key: String, path: String, language: String? = null): Transcript {
        val bytes = readFile(path) ?: return Transcript.Text(null)
        val lang = language?.let { "&language=$it" } ?: "&detect_language=true"
        val response = http.post("https://api.deepgram.com/v1/listen?model=nova-3&smart_format=true$lang") {
            header("Authorization", "Token $key")
            header(HttpHeaders.ContentType, "audio/wav")
            setBody(bytes)
        }
        if (!response.status.isSuccess()) return Transcript.Failed(troubleFor(response.status.value) ?: VoiceTrouble.Unreachable)
        val channels = json(response.bodyAsText())?.get("results")?.jsonObject?.get("channels")
            as? kotlinx.serialization.json.JsonArray ?: return Transcript.Text(null)
        val alternatives = channels.firstOrNull()?.jsonObject?.get("alternatives") as? kotlinx.serialization.json.JsonArray
        return Transcript.Text(alternatives?.firstOrNull()?.jsonObject?.get("transcript")?.jsonPrimitive?.contentOrNull)
    }

    /** [language]: a hint for Soniox ("hi"); it detects the language either way. */
    private suspend fun soniox(key: String, path: String, language: String? = null): Transcript {
        val bytes = readFile(path) ?: return Transcript.Text(null)
        var fileId: String? = null
        var jobId: String? = null
        try {
            val uploaded = http.submitFormWithBinaryData("https://api.soniox.com/v1/files", formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "filename=\"listen.wav\"")
                })
            }) {
                header("Authorization", "Bearer $key")
            }
            if (!uploaded.status.isSuccess()) return Transcript.Failed(troubleFor(uploaded.status.value) ?: VoiceTrouble.Unreachable)
            fileId = json(uploaded.bodyAsText())?.get("id")?.jsonPrimitive?.contentOrNull ?: return Transcript.Failed(VoiceTrouble.Unreachable)
            val created = http.post("https://api.soniox.com/v1/transcriptions") {
                header("Authorization", "Bearer $key")
                setBody(TextContent(buildJsonObject {
                    put("model", "stt-async-v3")
                    put("file_id", fileId)
                    language?.let { putJsonArray("language_hints") { add(JsonPrimitive(it)) } }
                }.toString(), ContentType.Application.Json))
            }
            if (!created.status.isSuccess()) return Transcript.Failed(troubleFor(created.status.value) ?: VoiceTrouble.Unreachable)
            jobId = json(created.bodyAsText())?.get("id")?.jsonPrimitive?.contentOrNull ?: return Transcript.Failed(VoiceTrouble.Unreachable)
            repeat(12) {
                delay(500)
                val job = http.get("https://api.soniox.com/v1/transcriptions/$jobId") {
                    header("Authorization", "Bearer $key")
                }
                val body = json(job.bodyAsText()) ?: return@repeat
                val status = body["status"]?.jsonPrimitive?.contentOrNull
                if (status == "error") return Transcript.Failed(VoiceTrouble.Unreachable)
                if (status == "completed") {
                    return Transcript.Text(body["text"]?.jsonPrimitive?.contentOrNull
                        ?: http.get("https://api.soniox.com/v1/transcriptions/$jobId/transcript") {
                            header("Authorization", "Bearer $key")
                        }.let { json(it.bodyAsText())?.get("text")?.jsonPrimitive?.contentOrNull })
                }
            }
            return Transcript.Failed(VoiceTrouble.Unreachable)
        } finally {
            fileId?.let { id ->
                runCatching {
                    http.delete("https://api.soniox.com/v1/files/$id") { header("Authorization", "Bearer $key") }
                }
            }
            jobId?.let { id ->
                runCatching {
                    http.delete("https://api.soniox.com/v1/transcriptions/$id") { header("Authorization", "Bearer $key") }
                }
            }
        }
    }

    private fun readFile(path: String): ByteArray? = fileReader(path)

    public companion object {
        /** Installed by the phone so a recording can be read back. Tests set their own. */
        public var fileReader: (String) -> ByteArray? = { null }

        public fun installReader(read: (String) -> ByteArray?) {
            fileReader = read
        }
        private const val FISH_TTS = "https://api.fish.audio/v1/tts"
        private val MAX_PREVIEW = 30.seconds
        /** ElevenLabs' default voice until the user picks one. */
        private const val ELEVEN_DEFAULT_VOICE = "21m00Tcm4TlvDq8ikWAM"
        private fun elevenUrl(voiceId: String?) = "https://api.elevenlabs.io/v1/text-to-speech/${voiceId ?: ELEVEN_DEFAULT_VOICE}"
        /** What a voice says when the user plays it in the picker. */
        public const val SAMPLE: String = "I'll turn on Dark Mode."
        /** The tag on Fish Audio's most used voices; the app shows its own words for it. */
        public const val POPULAR: String = "popular"
        /** The tag on the voice Mode suggests ([VoiceKind.ourPick]); the app shows its own words for it. */
        public const val OUR_PICK: String = "our-pick"

        /** [options] with Mode's suggested voice for [kind] first and tagged. */
        public fun withOurPick(kind: VoiceKind, options: List<VoiceOption>): List<VoiceOption> {
            val name = VoiceKind.ourPick(kind) ?: return options
            val (pick, rest) = options.partition { it.name.equals(name, ignoreCase = true) || it.name.startsWith("$name ", ignoreCase = true) }
            return pick.take(1).map { it.copy(tag = OUR_PICK) } + pick.drop(1) + rest
        }

        /** Three previews, preserving a voice chosen from the full catalog as the last row. */
        public fun previewVoices(kind: VoiceKind, options: List<VoiceOption>, selected: String?, limit: Int = 3): List<VoiceOption> {
            require(limit > 0)
            val ranked = withOurPick(kind, options)
            val first = ranked.take(limit)
            val kept = ranked.firstOrNull { it.id == selected } ?: return first
            return if (first.any { it.id == kept.id }) first else first.take(limit - 1) + kept
        }

        /** "en" → "English", "hi" → "Hindi" (Fish Audio lists ISO codes); unknown codes as written. */
        internal fun languageName(code: String): String = LANGUAGES[code.lowercase().substringBefore('-')] ?: code
        private val LANGUAGES = mapOf(
            "en" to "English", "hi" to "Hindi", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "es" to "Spanish",
            "fr" to "French", "de" to "German", "pt" to "Portuguese", "ar" to "Arabic", "ru" to "Russian", "it" to "Italian",
            "bn" to "Bengali", "ta" to "Tamil", "te" to "Telugu", "mr" to "Marathi",
        )

        /** 1234567 → "1.2M uses". */
        internal fun usesLabel(n: Long): String = when {
            n >= 1_000_000 -> "${(n / 100_000) / 10.0}M uses".replace(".0M", "M")
            n >= 1_000 -> "${n / 1_000}K uses"
            else -> "$n uses"
        }
        private val MAX_RECORD_MS = 20.seconds.inWholeMilliseconds
        private val SILENCE_MS = TurnEnd.PAUSE_MS
        private val PER_HEAR = 8.seconds
        private val TOTAL_HEAR = 20.seconds
    }
}

private fun json(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()

/** What an HTTP refusal means for the user: a bad key, or a plan that doesn't allow it. Others: unreachable. */
internal fun troubleFor(status: Int): VoiceTrouble? = when (status) {
    401 -> VoiceTrouble.KeyRejected
    402, 403, 429 -> VoiceTrouble.PlanLimit
    else -> null
}
