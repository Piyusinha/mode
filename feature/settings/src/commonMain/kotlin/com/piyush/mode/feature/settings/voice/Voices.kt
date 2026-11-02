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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
                when (kind) {
                    VoiceKind.Apple -> if (device.speakLine(line)) Spoken.Started else Spoken.NotStarted
                    VoiceKind.Fish -> cloudSpeak("https://api.fish.audio/v1/tts", settings.voiceKey(kind)!!, fish = true, line)
                    VoiceKind.ElevenLabs -> cloudSpeak(ELEVEN, settings.voiceKey(kind)!!, fish = false, line)
                    else -> Spoken.NotStarted
                }
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

    private fun order(hearing: Boolean): List<VoiceKind> {
        val ids = if (hearing) settings.settings.value.hearingOrder else settings.settings.value.speechOrder
        return ids.mapNotNull(VoiceKind::fromId)
    }

    private suspend fun cloudSpeak(url: String, key: String, fish: Boolean, text: String): Spoken {
        val response = http.post(url) {
            if (fish) {
                header("Authorization", "Bearer $key")
                header("model", "s1")
            } else {
                header("xi-api-key", key)
            }
            setBody(TextContent(speechBody(fish, text), ContentType.Application.Json))
        }
        if (!response.status.isSuccess()) {
            AppLog.log("voice", "speech ${response.status.value}")
            return Spoken.NotStarted
        }
        val bytes = response.bodyAsBytes()
        if (bytes.isEmpty()) return Spoken.NotStarted
        return if (device.playClip(bytes)) Spoken.Started else Spoken.NotStarted
    }

    private fun speechBody(fish: Boolean, text: String): String = buildJsonObject {
        put("text", text)
        if (fish) put("format", "mp3") else put("model_id", "eleven_flash_v2_5")
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
        private const val ELEVEN = "https://api.elevenlabs.io/v1/text-to-speech/21m00Tcm4TlvDq8ikWAM"
        private val MAX_RECORD_MS = 20.seconds.inWholeMilliseconds
        private val SILENCE_MS = 1200L
        private val PER_HEAR = 8.seconds
        private val TOTAL_HEAR = 20.seconds
    }
}

private fun json(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
