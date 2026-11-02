package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/** Who is signed in with a provider, as far as it says. */
@Serializable
data class AccountInfo(val email: String? = null, val name: String? = null, val plan: String? = null)

/** One usage limit of a plan: how much of it is used, and when it starts again. */
data class UsageWindow(val kind: Kind, val usedFraction: Double, val resetsAtMillis: Long?) {
    enum class Kind { Session, Week }
}

/** OpenRouter's key and balance. Amounts in US dollars; null where OpenRouter has no limit. */
data class OpenRouterBalance(val label: String?, val creditsTotal: Double?, val creditsUsed: Double?, val keyLimit: Double?, val keyRemaining: Double?) {
    val creditsLeft: Double? get() = if (creditsTotal != null && creditsUsed != null) (creditsTotal - creditsUsed).coerceAtLeast(0.0) else keyRemaining
}

/** The claims of a JSON Web Token (no signature check: only for showing who signed in). */
object Jwt {
    fun claims(token: String?): JsonObject? {
        val payload = token?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
            Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        }.getOrNull()
    }

    /** Email, name and ChatGPT plan from a ChatGPT sign-in's ID token. */
    fun chatGptAccount(idToken: String?): AccountInfo? {
        val c = claims(idToken) ?: return null
        fun s(o: JsonObject?, k: String) = o?.get(k)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val auth = runCatching { c["https://api.openai.com/auth"]?.jsonObject }.getOrNull()
        return AccountInfo(s(c, "email"), s(c, "name"), s(auth, "chatgpt_plan_type")?.let(::planName))
    }

    /** "plus" → "Plus", "pro" → "Pro". */
    fun planName(raw: String): String = raw.replaceFirstChar { it.uppercase() }
}

/**
 * Claude's account and usage for a subscription sign-in: the endpoints Claude Code itself uses for its
 * profile and its /usage view. Both are optional: when Claude does not answer, Mode shows nothing rather
 * than a guess.
 */
class ClaudeAccountApi(private val http: HttpClient, private val base: String = "https://api.anthropic.com") {
    suspend fun profile(token: String): AccountInfo? {
        val o = get("$base/api/oauth/profile", token) ?: return null
        val account = runCatching { o["account"]?.jsonObject }.getOrNull()
        fun s(obj: JsonObject?, vararg keys: String) = keys.firstNotNullOfOrNull { obj?.get(it)?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
        fun b(key: String) = account?.get(key)?.jsonPrimitive?.booleanOrNull == true
        val plan = when {
            b("has_claude_max") -> "Max"
            b("has_claude_pro") -> "Pro"
            else -> s(runCatching { o["organization"]?.jsonObject }.getOrNull(), "organization_type", "rate_limit_tier")?.let(Jwt::planName)
        }
        return AccountInfo(s(account, "email", "email_address"), s(account, "display_name", "full_name"), plan)
    }

    suspend fun usage(token: String): List<UsageWindow> {
        val o = get("$base/api/oauth/usage", token) ?: return emptyList()
        fun window(key: String, kind: UsageWindow.Kind): UsageWindow? {
            val w = runCatching { o[key]?.jsonObject }.getOrNull() ?: return null
            val used = w["utilization"]?.jsonPrimitive?.doubleOrNull ?: return null
            val resets = w["resets_at"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
            // Claude reports percent (34.0); a fraction (0.34) is accepted too.
            return UsageWindow(kind, (if (used > 1.0) used / 100 else used).coerceIn(0.0, 1.0), resets)
        }
        return listOfNotNull(window("five_hour", UsageWindow.Kind.Session), window("seven_day", UsageWindow.Kind.Week))
    }

    private suspend fun get(url: String, token: String): JsonObject? = runCatching {
        val response = http.get(url) {
            header("Authorization", "Bearer $token")
            header("anthropic-beta", "oauth-2025-04-20")
        }
        if (!response.status.isSuccess()) return null
        Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }.getOrNull()
}

/** OpenRouter's key name and balance (openrouter.ai/docs: /api/v1/key and /api/v1/credits). */
class OpenRouterAccountApi(private val http: HttpClient, private val base: String = "https://openrouter.ai/api/v1") {
    suspend fun balance(apiKey: String): OpenRouterBalance? {
        val key = get("$base/key", apiKey)?.let { runCatching { it["data"]?.jsonObject }.getOrNull() }
        val credits = get("$base/credits", apiKey)?.let { runCatching { it["data"]?.jsonObject }.getOrNull() }
        if (key == null && credits == null) return null
        fun d(o: JsonObject?, k: String) = o?.get(k)?.jsonPrimitive?.doubleOrNull
        return OpenRouterBalance(
            label = key?.get("label")?.jsonPrimitive?.contentOrNull,
            creditsTotal = d(credits, "total_credits"), creditsUsed = d(credits, "total_usage"),
            keyLimit = d(key, "limit"), keyRemaining = d(key, "limit_remaining"),
        )
    }

    private suspend fun get(url: String, apiKey: String): JsonObject? = runCatching {
        val response = http.get(url) { header("Authorization", "Bearer $apiKey") }
        if (!response.status.isSuccess()) return null
        Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }.getOrNull()
}
