package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.storage.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** A newer Mode than this one: its version ("0.1.4") and its release page (what's new). */
public data class ModeUpdate(val version: String, val notesUrl: String)

/**
 * Is a newer Mode out? Asks GitHub for Mode's releases ("Mode-Mobile-Build-0.1.4"; the first ones were "v0.1.x")
 * at most once a day, and remembers the answer between launches. Nothing is downloaded or installed: Mode Setup
 * updates Mode, and Settings says so.
 */
public class ModeUpdates(
    private val http: HttpClient,
    private val prefs: KeyValueStore,
    /** This Mode's version ("0.1.3"). */
    public val current: String,
    private val now: () -> Long = ::nowMs,
    private val repo: String = "Piyusinha/mode",
) {
    private val newest = MutableStateFlow(cached())

    /** The newer Mode, or null when this one is the newest (or nothing is known yet). */
    public val available: StateFlow<ModeUpdate?> get() = availableState.asStateFlow()
    private val availableState = MutableStateFlow(newest.value?.takeIf { isNewer(it.version, current) })

    /** Checks GitHub, unless it was checked in the last day ([force] checks anyway). Offline: keeps what it knew. */
    public suspend fun check(force: Boolean = false) {
        val last = prefs.getString(CHECKED_AT)?.toLongOrNull()
        if (!force && last != null && now() - last < DAY_MS) return
        val found = runCatching {
            val res = http.get("https://api.github.com/repos/$repo/releases?per_page=50") {
                header("Accept", "application/vnd.github+json")
            }
            if (res.status.isSuccess()) newestOf(res.bodyAsText()) else null
        }.getOrNull() ?: return
        prefs.putString(CHECKED_AT, now().toString())
        prefs.putString(NEWEST, "${found.version}\n${found.notesUrl}")
        newest.value = found
        availableState.value = found.takeIf { isNewer(it.version, current) }
    }

    private fun cached(): ModeUpdate? = prefs.getString(NEWEST)?.split('\n')?.takeIf { it.size == 2 }?.let { ModeUpdate(it[0], it[1]) }

    public companion object {
        private const val CHECKED_AT = "updates.checkedAt"
        private const val NEWEST = "updates.newest"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val TAG = Regex("""^(?:Mode-Mobile-Build-|v)(\d+(?:\.\d+)*)$""")

        /** The newest published Mode release with Mode.ipa in GitHub's release list, by version. */
        public fun newestOf(json: String): ModeUpdate? {
            val list = Json.parseToJsonElement(json) as? JsonArray ?: return null
            return list.mapNotNull { e ->
                val r = e.jsonObject
                if (r["draft"]?.jsonPrimitive?.booleanOrNull == true || r["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
                val version = TAG.find(r["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty())?.groupValues?.get(1) ?: return@mapNotNull null
                val hasIpa = r["assets"]?.jsonArray.orEmpty().any { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == "Mode.ipa" }
                if (!hasIpa) return@mapNotNull null
                ModeUpdate(version, r["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/Piyusinha/mode/releases")
            }.reduceOrNull { a, b -> if (isNewer(b.version, a.version)) b else a }
        }

        /** "0.1.10" is newer than "0.1.9"; unreadable versions are never newer. */
        public fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: return false }
            val b = current.split('.').map { it.toIntOrNull() ?: return false }
            for (i in 0 until maxOf(a.size, b.size)) {
                val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }
}
