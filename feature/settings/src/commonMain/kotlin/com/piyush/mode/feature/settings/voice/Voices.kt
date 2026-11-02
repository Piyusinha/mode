package com.piyush.mode.feature.settings.voice

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

/**
 * Speaks and hears in the user's order. A cloud failure before any sound, or an empty
 * transcript, tries the next ready choice. Apple is skipped when it is not ready.
 */
public class Voices(
    private val settings: SettingsRepository,
    private val http: HttpClient,
    private val device: DeviceVoice = DeviceVoice,
) {
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

    public suspend fun speak(text: String) {
        val line = text.trim()
        if (line.isEmpty()) return
        for (kind in order(hearing = false)) {
            if (block(kind, hearing = false) != null) continue
            val spoken = try {
                say(kind, settings.settings.value.voices[kind.id]?.id, line)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.log("voice", "${kind.id} speech failed before sound: ${e.message}")
                Spoken.NotStarted
            }
            if (spoken == Spoken.Started) return
        }
    }

    /** One recording, then each ready hearer, in order. */
    public suspend fun hear(): Heard {
        val ready = order(hearing = true).filter { block(it, hearing = true) == null }
        if (ready.isEmpty()) return Heard.Nothing
        val path = device.recordOnce(MAX_RECORD_MS, SILENCE_MS) ?: return Heard.Silence
        return try {
            val deadline = TimeSource.Monotonic.markNow() + TOTAL_HEAR
            for (kind in ready) {
                if (deadline.hasPassedNow()) break
                val text = withTimeoutOrNull(PER_HEAR) {
                    when (kind) {
                        VoiceKind.Apple -> device.transcribeApple(path)
                        VoiceKind.Soniox -> soniox(settings.voiceKey(kind)!!, path)
                        VoiceKind.Deepgram -> deepgram(settings.voiceKey(kind)!!, path)
                        else -> null
                    }
                }
                if (!text.isNullOrBlank()) return Heard.Words(text.trim())
                AppLog.log("voice", "${kind.id} heard nothing")
            }
            Heard.Nothing
        } finally {
            device.deleteRecording(path)
        }
    }

    /** [line] in [voiceId] (null: the provider's default), at the user's speed. */
    private suspend fun say(kind: VoiceKind, voiceId: String?, line: String): Spoken {
        val speed = settings.settings.value.speechSpeed.factor
        return when (kind) {
            VoiceKind.Apple -> if (device.speakLine(line, voiceId, speed)) Spoken.Started else Spoken.NotStarted
            VoiceKind.Fish -> cloudSpeak(FISH_TTS, settings.voiceKey(kind)!!, fish = true, line, voiceId, speed)
            VoiceKind.ElevenLabs -> cloudSpeak(elevenUrl(voiceId), settings.voiceKey(kind)!!, fish = false, line, voiceId, speed)
            else -> Spoken.NotStarted
        }
    }

    /** The speech provider that would speak now (the first ready one), and the voice picked there. */
    public fun speaker(): Pair<VoiceKind, VoiceChoice?>? =
        order(hearing = false).firstOrNull { block(it, hearing = false) == null }?.let { it to settings.settings.value.voices[it.id] }

    /** The hearing provider that would listen now (the first ready one). */
    public fun listener(): VoiceKind? = order(hearing = true).firstOrNull { block(it, hearing = true) == null }

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
            if (clip != null) device.playClip(clip) else say(kind, option.id.ifEmpty { null }, SAMPLE) == Spoken.Started
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

    private suspend fun cloudSpeak(url: String, key: String, fish: Boolean, text: String, voiceId: String?, speed: Double): Spoken {
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
            return Spoken.NotStarted
        }
        val bytes = response.bodyAsBytes()
        if (bytes.isEmpty()) return Spoken.NotStarted
        return if (device.playClip(bytes)) Spoken.Started else Spoken.NotStarted
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

    private suspend fun deepgram(key: String, path: String): String? {
        val bytes = readFile(path) ?: return null
        val response = http.post("https://api.deepgram.com/v1/listen?model=nova-3&smart_format=true") {
            header("Authorization", "Token $key")
            header(HttpHeaders.ContentType, "audio/wav")
            setBody(bytes)
        }
        if (!response.status.isSuccess()) return null
        val channels = json(response.bodyAsText())?.get("results")?.jsonObject?.get("channels")
            as? kotlinx.serialization.json.JsonArray ?: return null
        val alternatives = channels.firstOrNull()?.jsonObject?.get("alternatives") as? kotlinx.serialization.json.JsonArray
        return alternatives?.firstOrNull()?.jsonObject?.get("transcript")?.jsonPrimitive?.contentOrNull
    }

    private suspend fun soniox(key: String, path: String): String? {
        val bytes = readFile(path) ?: return null
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
            if (!uploaded.status.isSuccess()) return null
            fileId = json(uploaded.bodyAsText())?.get("id")?.jsonPrimitive?.contentOrNull ?: return null
            val created = http.post("https://api.soniox.com/v1/transcriptions") {
                header("Authorization", "Bearer $key")
                setBody(TextContent(buildJsonObject {
                    put("model", "stt-async-v3")
                    put("file_id", fileId)
                }.toString(), ContentType.Application.Json))
            }
            if (!created.status.isSuccess()) return null
            jobId = json(created.bodyAsText())?.get("id")?.jsonPrimitive?.contentOrNull ?: return null
            repeat(12) {
                delay(500)
                val job = http.get("https://api.soniox.com/v1/transcriptions/$jobId") {
                    header("Authorization", "Bearer $key")
                }
                val body = json(job.bodyAsText()) ?: return@repeat
                val status = body["status"]?.jsonPrimitive?.contentOrNull
                if (status == "error") return null
                if (status == "completed") {
                    return body["text"]?.jsonPrimitive?.contentOrNull
                        ?: http.get("https://api.soniox.com/v1/transcriptions/$jobId/transcript") {
                            header("Authorization", "Bearer $key")
                        }.let { json(it.bodyAsText())?.get("text")?.jsonPrimitive?.contentOrNull }
                }
            }
            return null
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
        private val SILENCE_MS = 1200L
        private val PER_HEAR = 8.seconds
        private val TOTAL_HEAR = 20.seconds
    }
}

private fun json(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
